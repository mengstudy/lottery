# -*- coding: utf-8 -*-
"""
视频搜索模块
实现 TVBox 订阅源的动态解密与公开影视采集站 CMS 接口的并发检索
"""
import requests
import base64
import json
from concurrent.futures import ThreadPoolExecutor, as_completed

# 聚合使用的公开高速 CMS 采集站接口列表
CMS_SOURCES = {
    "量子资源": "https://cj.lziapi.com/api.php/provide/vod/",
    "非凡资源": "http://cj.ffzyapi.com/api.php/provide/vod/",
    "红牛资源": "https://www.hongniuzy2.com/api.php/provide/vod/",
    "暴风资源": "https://bfzyapi.com/api.php/provide/vod/"
}


def decrypt_tvbox_config():
    """
    动态获取并解密饭太硬的 TVBox 配置源
    
    Returns:
        dict: 解密后的 JSON 配置字典，失败则返回空字典
    """
    url = "http://www.饭太硬.cc/tv"
    try:
        # 不使用定制请求头，以防服务器识别为浏览器后返回导航HTML而不是配置图片
        response = requests.get(url, timeout=10)
        if response.status_code != 200:
            return {}
        
        data = response.content
        # 寻找 JPG 图片结束标志 \xFF\xD9
        ffd9_idx = data.find(b'\xFF\xD9')
        if ffd9_idx == -1:
            return {}
            
        extra_data = data[ffd9_idx + 2:]
        extra_str = extra_data.decode('utf-8', errors='ignore')
        
        # 寻找 ** 符号，分割出真实的 Base64 密文
        if '**' in extra_str:
            parts = extra_str.split('**', 1)
            base64_part = parts[1].strip()
            decoded_bytes = base64.b64decode(base64_part)
            decoded_str = decoded_bytes.decode('utf-8')
            return json.loads(decoded_str)
        else:
            # 兼容直接 Base64 的情况
            decoded_bytes = base64.b64decode(extra_str.strip())
            decoded_str = decoded_bytes.decode('utf-8')
            return json.loads(decoded_str)
            
    except Exception as e:
        print(f"解密 TVBox 订阅源失败: {e}")
        return {}


def search_single_source(name, url, keyword):
    """
    在单个采集站源中检索视频
    
    Args:
        name (str): 采集站名称
        url (str): 接口 URL
        keyword (str): 关键词
        
    Returns:
        list: 该站点的检索结果列表
    """
    try:
        params = {
            "ac": "detail",
            "wd": keyword
        }
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        response = requests.get(url, params=params, headers=headers, timeout=6)
        if response.status_code != 200:
            return []
            
        data = response.json()
        results = []
        if "list" in data and isinstance(data["list"], list):
            for item in data["list"]:
                play_from = item.get("vod_play_from", "")
                play_url_raw = item.get("vod_play_url", "")
                
                # 播放线路解析
                play_lines = {}
                from_list = [f.strip() for f in play_from.split("$$$") if f.strip()]
                url_list = [u.strip() for u in play_url_raw.split("$$$") if u.strip()]
                
                for idx, from_name in enumerate(from_list):
                    if idx < len(url_list):
                        raw_line_url = url_list[idx]
                        episodes = []
                        # 分割单条线路下的各分集
                        for ep_str in raw_line_url.split("#"):
                            if "$" in ep_str:
                                parts = ep_str.split("$", 1)
                                episodes.append({
                                    "name": parts[0].strip(),
                                    "url": parts[1].strip()
                                })
                            elif ep_str.strip():
                                episodes.append({
                                    "name": "播放直链",
                                    "url": ep_str.strip()
                                })
                        if episodes:
                            play_lines[from_name] = episodes
                            
                # 兼容不包含 $$$ 只有单线路的源
                if not play_lines and play_url_raw:
                    episodes = []
                    for ep_str in play_url_raw.split("#"):
                        if "$" in ep_str:
                            parts = ep_str.split("$", 1)
                            episodes.append({
                                "name": parts[0].strip(),
                                "url": parts[1].strip()
                            })
                        elif ep_str.strip():
                            episodes.append({
                                "name": "播放直链",
                                "url": ep_str.strip()
                            })
                    if episodes:
                        play_lines[play_from or "默认源"] = episodes
                
                results.append({
                    "name": item.get("vod_name", ""),
                    "type": item.get("type_name", ""),
                    "actor": item.get("vod_actor", ""),
                    "director": item.get("vod_director", ""),
                    "remarks": item.get("vod_remarks", ""),
                    "desc": item.get("vod_content", ""),
                    "pic": item.get("vod_pic", ""),
                    "updated": item.get("vod_time", ""),
                    "source": name,
                    "play_lines": play_lines
                })
        return results
    except Exception as e:
        print(f"请求采集站 [{name}] 检索失败: {e}")
        return []


def search_aggregate(keyword):
    """
    在所有采集站中并发检索并去重合并
    
    Args:
        keyword (str): 检索关键词
        
    Returns:
        list: 合并同一影视在不同源下的数据后的聚合结果列表
    """
    if not keyword:
        return []
        
    aggregated = []
    # 使用 ThreadPoolExecutor 并发检索，缩减等待时间
    with ThreadPoolExecutor(max_workers=5) as executor:
        futures = {
            executor.submit(search_single_source, name, url, keyword): name 
            for name, url in CMS_SOURCES.items()
        }
        for future in as_completed(futures):
            results = future.result()
            if results:
                aggregated.extend(results)
                
    # 依据视频名字和类型进行归类合并
    merged = {}
    for item in aggregated:
        key = (item["name"], item["type"])
        if key not in merged:
            merged[key] = {
                "name": item["name"],
                "type": item["type"],
                "actor": item["actor"],
                "director": item["director"],
                "remarks": item["remarks"],
                "desc": item["desc"],
                "pic": item["pic"],
                "updated": item["updated"],
                # 按 { 采集站名: 播放线路字典 } 的映射存放
                "sources": {item["source"]: item["play_lines"]}
            }
        else:
            # 同名视频，进行播放线路的合并
            merged[key]["sources"][item["source"]] = item["play_lines"]
            
            # 若已有数据里关键字段为空，则进行补充
            if not merged[key]["pic"] and item["pic"]:
                merged[key]["pic"] = item["pic"]
            if not merged[key]["desc"] and item["desc"]:
                merged[key]["desc"] = item["desc"]
            if not merged[key]["actor"] and item["actor"]:
                merged[key]["actor"] = item["actor"]
            if not merged[key]["director"] and item["director"]:
                merged[key]["director"] = item["director"]
                
    return list(merged.values())


def search_stream_generator(keyword):
    """
    流式检索生成器：每就绪一个源，即刻 yield 该源的检索结果
    
    Args:
        keyword (str): 检索关键词
        
    Yields:
        dict: {"source": 采集站名称, "results": 该站检索结果列表, "total_sources": 总源数}
    """
    if not keyword:
        return

    total_sources = len(CMS_SOURCES)
    with ThreadPoolExecutor(max_workers=total_sources) as executor:
        futures = {
            executor.submit(search_single_source, name, url, keyword): name 
            for name, url in CMS_SOURCES.items()
        }
        for future in as_completed(futures):
            site_name = futures[future]
            try:
                results = future.result()
            except Exception as e:
                print(f"流式检索源 [{site_name}] 异常: {e}")
                results = []
            yield {
                "source": site_name,
                "results": results,
                "total_sources": total_sources
            }

