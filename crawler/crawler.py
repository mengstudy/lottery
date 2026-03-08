"""
爬虫核心逻辑模块
负责抓取福彩网数据并解析开奖结果
"""
import requests
from bs4 import BeautifulSoup
import time
import logging
import re
from datetime import datetime
from typing import List, Dict, Optional
from pathlib import Path

from .config import (
    API_BASE_URL, API_PARAMS, BASE_URL, BACKUP_BASE_URL, HEADERS, 
    REQUEST_DELAY, TIMEOUT, MAX_RETRIES, RETRY_DELAY
)
from database.db_manager import db_manager

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(Path(__file__).parent.parent / 'crawler.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


class SSQCrawler:
    """双色球爬虫类"""
    
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update(HEADERS)
        
    def _fetch_json(self, url: str, params: dict = None, retry_count: int = 0) -> Optional[dict]:
        """
        获取 JSON 数据（用于 API 接口）
        
        Args:
            url: API URL
            params: 请求参数
            retry_count: 当前重试次数
            
        Returns:
            JSON 数据，失败返回 None
        """
        try:
            response = self.session.get(url, params=params, timeout=TIMEOUT)
            response.raise_for_status()
            response.encoding = 'utf-8'
            time.sleep(REQUEST_DELAY)  # 避免请求过快
            return response.json()
            
        except requests.exceptions.RequestException as e:
            logger.error(f"API 请求失败：{e}")
            if retry_count < MAX_RETRIES:
                logger.info(f"将在 {RETRY_DELAY} 秒后重试...（第 {retry_count + 1}/{MAX_RETRIES} 次）")
                time.sleep(RETRY_DELAY)
                return self._fetch_json(url, params, retry_count + 1)
            else:
                logger.error("达到最大重试次数，放弃请求")
                return None
        except Exception as e:
            logger.error(f"JSON 解析失败：{e}")
            return None
    
    def _parse_api_response(self, json_data: dict) -> List[Dict]:
        """
        解析福彩网 API 响应数据
        
        Args:
            json_data: API 返回的 JSON 数据
            
        Returns:
            开奖数据列表
        """
        results = []
        
        try:
            # API 返回的数据结构
            if not json_data or 'result' not in json_data:
                logger.warning("API 返回数据格式异常")
                return results
            
            data = json_data.get('result', [])
            
            for item in data:
                try:
                    # 提取期号（字段名：code）
                    issue = item.get('code', '')
                    if not issue or not re.match(r'\d{6}', issue):
                        continue
                    
                    # 提取红球号码（字段名：red，逗号分隔的字符串）
                    red_ball_str = item.get('red', '')
                    if not red_ball_str:
                        continue
                    red_balls = [int(x.strip()) for x in red_ball_str.split(',') if x.strip()]
                    
                    # 验证红球数量和范围
                    if len(red_balls) != 6:
                        logger.warning(f"红球数量不正确：{red_balls}")
                        continue
                    
                    # 提取蓝球号码（字段名：blue）
                    blue_ball_str = item.get('blue', '')
                    if not blue_ball_str:
                        continue
                    blue_ball = int(blue_ball_str)
                    
                    # 验证蓝球范围
                    if not 1 <= blue_ball <= 16:
                        logger.warning(f"蓝球号码超出范围：{blue_ball}")
                        continue
                    
                    # 提取开奖日期（字段名：date，格式如 "2026-03-05(四)"）
                    date_str = item.get('date', '')
                    if not date_str:
                        continue
                    # 去除括号内的星期
                    date_str_clean = date_str.split('(')[0].strip()
                    draw_date = self._parse_date(date_str_clean)
                    
                    # 提取中奖地区（字段名：content）
                    region_info = item.get('content', '')
                    
                    if draw_date:
                        results.append({
                            'issue': issue.zfill(6),
                            'red_balls': red_balls,
                            'blue_ball': blue_ball,
                            'draw_date': draw_date.strftime('%Y-%m-%d'),
                            'region': region_info
                        })
                        
                except (ValueError, KeyError, TypeError) as e:
                    logger.warning(f"解析单条数据失败：{e}, 数据：{item}")
                    continue
            
        except Exception as e:
            logger.error(f"解析 API 响应失败：{e}")
        
        return results
    
    def fetch_all_history_by_api(self, max_pages: int = 100) -> List[Dict]:
        """
        通过 API 抓取所有历史数据
        
        Args:
            max_pages: 最大抓取页数（每页 30 条）
            
        Returns:
            开奖数据列表
        """
        logger.info("开始通过 API 抓取历史数据...")
        all_results = []
        current_page = 1
        
        while current_page <= max_pages:
            # 构建 API 参数
            params = API_PARAMS.copy()
            params['pageNo'] = current_page
            
            logger.info(f"正在抓取第 {current_page} 页...")
            
            # 请求 API
            json_data = self._fetch_json(API_BASE_URL, params=params)
            
            if not json_data:
                logger.warning(f"第 {current_page} 页抓取失败")
                break
            
            # 解析数据
            page_results = self._parse_api_response(json_data)
            
            if not page_results:
                logger.info(f"第 {current_page} 页没有更多数据")
                break
            
            all_results.extend(page_results)
            logger.info(f"第 {current_page} 页成功抓取 {len(page_results)} 条数据，累计 {len(all_results)} 条")
            
            # 立即保存到数据库（每页保存一次）
            saved_count = 0
            for result in page_results:
                try:
                    db_manager.insert_lottery_result(result)
                    saved_count += 1
                except Exception as e:
                    logger.error(f"插入数据失败：{e}, 期号：{result.get('issue', 'N/A')}")
                    continue
            
            logger.info(f"第 {current_page} 页已保存 {saved_count} 条到数据库")
            
            # 检查是否还有下一页
            # 可以通过判断返回数据是否少于 pageSize 来确定
            if len(page_results) < params['pageSize']:
                logger.info("已抓取到最后一页")
                break
            
            current_page += 1
            
            # 添加延迟，避免请求过快
            time.sleep(REQUEST_DELAY)
        
        logger.info(f"历史数据抓取完成，共 {len(all_results)} 条")
        return all_results
    
    def _fetch_page(self, url: str, retry_count: int = 0) -> Optional[str]:
        """
        获取网页内容
        
        Args:
            url: 目标 URL
            retry_count: 当前重试次数
            
        Returns:
            网页 HTML 内容，失败返回 None
        """
        try:
            response = self.session.get(url, timeout=TIMEOUT)
            response.raise_for_status()
            response.encoding = 'utf-8'
            time.sleep(REQUEST_DELAY)  # 避免请求过快
            return response.text
            
        except requests.exceptions.RequestException as e:
            logger.error(f"请求失败：{e}")
            if retry_count < MAX_RETRIES:
                logger.info(f"将在 {RETRY_DELAY} 秒后重试...（第 {retry_count + 1}/{MAX_RETRIES} 次）")
                time.sleep(RETRY_DELAY)
                return self._fetch_page(url, retry_count + 1)
            else:
                logger.error("达到最大重试次数，放弃请求")
                return None
    
    def _parse_cwl_gov(self, html: str) -> List[Dict]:
        """
        解析中国福彩网页面
        
        Args:
            html: 网页 HTML 内容
            
        Returns:
            开奖数据列表
        """
        results = []
        soup = BeautifulSoup(html, 'lxml')
        
        # 查找开奖数据表格（需要根据实际页面结构调整选择器）
        # 福彩网通常使用表格展示开奖数据
        tables = soup.find_all('table')
        
        for table in tables:
            rows = table.find_all('tr')
            for row in rows[1:]:  # 跳过表头
                cols = row.find_all('td')
                if len(cols) >= 8:  # 确保有足够的列
                    try:
                        # 提取期号
                        issue_text = cols[0].get_text(strip=True)
                        if not issue_text or not re.match(r'\d{6}', issue_text):
                            continue
                        
                        # 提取红球号码
                        red_balls = []
                        for i in range(1, 7):
                            ball_text = cols[i].get_text(strip=True)
                            red_balls.append(int(ball_text))
                        
                        # 提取蓝球号码
                        blue_ball = int(cols[7].get_text(strip=True))
                        
                        # 提取开奖日期
                        date_text = cols[-1].get_text(strip=True)
                        # 尝试多种日期格式
                        draw_date = self._parse_date(date_text)
                        
                        if draw_date:
                            results.append({
                                'issue': issue_text.zfill(6),
                                'red_balls': red_balls,
                                'blue_ball': blue_ball,
                                'draw_date': draw_date.strftime('%Y-%m-%d')
                            })
                            
                    except (ValueError, IndexError) as e:
                        logger.warning(f"解析行数据失败：{e}")
                        continue
        
        return results
    
    def _parse_500_com(self, html: str) -> List[Dict]:
        """
        解析 500 彩票网页面（备选方案）
        
        Args:
            html: 网页 HTML 内容
            
        Returns:
            开奖数据列表
        """
        results = []
        soup = BeautifulSoup(html, 'lxml')
        
        # 500 网的表格结构
        table = soup.find('table', {'id': 'tdata'})
        if not table:
            return results
        
        rows = table.find_all('tr')
        for row in rows:
            cols = row.find_all('td')
            if len(cols) >= 12:
                try:
                    issue_text = cols[0].get_text(strip=True)
                    if not issue_text.isdigit():
                        continue
                    
                    # 红球号码
                    red_balls = [int(cols[i].get_text(strip=True)) for i in range(1, 7)]
                    # 蓝球号码
                    blue_ball = int(cols[7].get_text(strip=True))
                    
                    # 开奖日期
                    date_text = cols[11].get_text(strip=True)
                    draw_date = self._parse_date(date_text)
                    
                    if draw_date:
                        results.append({
                            'issue': issue_text.zfill(6),
                            'red_balls': red_balls,
                            'blue_ball': blue_ball,
                            'draw_date': draw_date.strftime('%Y-%m-%d')
                        })
                        
                except (ValueError, IndexError) as e:
                    logger.warning(f"解析 500 网数据失败：{e}")
                    continue
        
        return results
    
    def _parse_date(self, date_str: str) -> Optional[datetime]:
        """
        解析日期字符串
        
        Args:
            date_str: 日期字符串
            
        Returns:
            datetime 对象，失败返回 None
        """
        date_formats = [
            '%Y-%m-%d',
            '%Y/%m/%d',
            '%Y年%m月%d日',
            '%y-%m-%d',
            '%y/%m/%d'
        ]
        
        for fmt in date_formats:
            try:
                return datetime.strptime(date_str, fmt)
            except ValueError:
                continue
        
        # 如果所有格式都失败，尝试从字符串中提取数字
        match = re.search(r'(\d{4})[-./](\d{1,2})[-./](\d{1,2})', date_str)
        if match:
            year, month, day = map(int, match.groups())
            return datetime(year, month, day)
        
        return None
    
    def fetch_latest_data(self) -> List[Dict]:
        """
        抓取最新开奖数据
        
        Returns:
            开奖数据列表
        """
        logger.info("开始抓取最新数据...")
        
        # 优先使用 API 抓取最新数据（第 1 页）
        params = API_PARAMS.copy()
        params['pageNo'] = 1
        json_data = self._fetch_json(API_BASE_URL, params=params)
        
        if json_data:
            results = self._parse_api_response(json_data)
            if results:
                logger.info(f"从福彩网 API 成功抓取 {len(results)} 条数据")
                
                # 立即保存到数据库
                saved_count = 0
                for result in results:
                    try:
                        db_manager.insert_lottery_result(result)
                        saved_count += 1
                    except Exception as e:
                        logger.error(f"插入数据失败：{e}, 期号：{result.get('issue', 'N/A')}")
                        continue
                
                logger.info(f"已保存 {saved_count} 条到数据库")
                return results
        
        # API 失败，尝试网页版
        logger.warning("API 抓取失败，尝试网页版...")
        html = self._fetch_page(BASE_URL)
        if html:
            results = self._parse_cwl_gov(html)
            if results:
                logger.info(f"从福彩网成功抓取 {len(results)} 条数据")
                return results
        
        # 官方源失败，使用备选源
        logger.warning("福彩网抓取失败，尝试使用 500 彩票网...")
        html = self._fetch_page(BACKUP_BASE_URL)
        if html:
            results = self._parse_500_com(html)
            if results:
                logger.info(f"从 500 网成功抓取 {len(results)} 条数据")
                return results
        
        logger.error("所有数据源均抓取失败")
        return []
    
    def fetch_all_history(self) -> List[Dict]:
        """
        抓取所有历史数据
        
        Returns:
            开奖数据列表
        """
        logger.info("开始抓取历史数据...")
        
        # 优先使用 API 抓取所有历史数据
        results = self.fetch_all_history_by_api(max_pages=100)
        if results:
            return results
        
        # API 失败，使用网页版（旧方法）
        logger.warning("API 抓取失败，尝试网页版...")
        all_results = []
        
        # 对于福彩网，需要遍历所有页面
        # 这里简化处理，只抓取最新数据
        # 实际使用中需要根据网站分页逻辑进行循环
        
        results = self.fetch_latest_data()
        if results:
            all_results.extend(results)
        
        logger.info(f"历史数据抓取完成，共 {len(all_results)} 条")
        return all_results
    
    def validate_result(self, result: Dict) -> bool:
        """
        验证开奖数据的有效性
        
        Args:
            result: 开奖数据字典
            
        Returns:
            是否有效
        """
        try:
            # 检查期号
            if not result.get('issue') or not result['issue'].isdigit():
                return False
            
            # 检查红球
            red_balls = result.get('red_balls', [])
            if len(red_balls) != 6:
                return False
            for ball in red_balls:
                if not 1 <= ball <= 33:
                    return False
            
            # 检查蓝球
            blue_ball = result.get('blue_ball')
            if not 1 <= blue_ball <= 16:
                return False
            
            # 检查日期
            if not result.get('draw_date'):
                return False
            
            return True
            
        except Exception as e:
            logger.error(f"数据验证失败：{e}")
            return False


# 单例模式
crawler = SSQCrawler()


if __name__ == "__main__":
    # 测试爬虫
    test_crawler = SSQCrawler()
    results = test_crawler.fetch_latest_data()
    
    if results:
        print(f"\n成功抓取 {len(results)} 条数据")
        print("\n最新一期数据:")
        print(results[0])
    else:
        print("抓取失败")
