"""
福彩快乐8爬虫模块
负责抓取福彩网快乐8数据并解析开奖结果
"""
import requests
import time
import logging
import re
from datetime import datetime
from typing import List, Dict, Optional
from pathlib import Path

from database.kl8_db_manager import kl8_db_manager

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

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
    'Accept': 'application/json, text/javascript, */*; q=0.01',
    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
    'Referer': 'https://www.cwl.gov.cn/ygkj/wqkjgg/kl8/',
    'X-Requested-With': 'XMLHttpRequest'
}

API_BASE_URL = "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice"
REQUEST_DELAY = 2.0
TIMEOUT = 10
MAX_RETRIES = 3
RETRY_DELAY = 3.0


class KL8Crawler:
    """快乐8爬虫类"""
    
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update(HEADERS)
        
    def _fetch_json(self, url: str, params: dict = None, retry_count: int = 0) -> Optional[dict]:
        try:
            response = self.session.get(url, params=params, timeout=TIMEOUT)
            response.raise_for_status()
            time.sleep(REQUEST_DELAY)
            return response.json()
            
        except requests.exceptions.RequestException as e:
            logger.error(f"快乐8 API 请求失败：{e}")
            if retry_count < MAX_RETRIES:
                logger.info(f"将在 {RETRY_DELAY} 秒后重试...（第 {retry_count + 1}/{MAX_RETRIES} 次）")
                time.sleep(RETRY_DELAY)
                return self._fetch_json(url, params, retry_count + 1)
            else:
                logger.error("快乐8达到最大重试次数，放弃请求")
                return None
        except Exception as e:
            logger.error(f"快乐8 JSON 解析失败：{e}")
            return None
    
    def _parse_api_response(self, json_data: dict) -> List[Dict]:
        results = []
        
        try:
            if not json_data or 'result' not in json_data:
                logger.warning("快乐8 API 返回数据格式异常")
                return results
            
            data = json_data.get('result', [])
            
            for item in data:
                try:
                    # 提取期号
                    issue = item.get('code', '')
                    if not issue or not re.match(r'\d+', issue):
                        continue
                    
                    # 提取20个红球 (快乐8开奖结果存放在'red'中，逗号分隔)
                    red_ball_str = item.get('red', '')
                    if not red_ball_str:
                        continue
                    red_balls = [int(x.strip()) for x in red_ball_str.split(',') if x.strip()]
                    
                    if len(red_balls) != 20:
                        logger.warning(f"快乐8红球数量不正确：{red_balls}")
                        continue
                    
                    # 提取日期，并清洗掉括号内的星期，如 2026-06-14(日) -> 2026-06-14
                    date_str = item.get('date', '')
                    if not date_str:
                        continue
                    date_str_clean = date_str.split('(')[0].strip()
                    draw_date = self._parse_date(date_str_clean)
                    
                    region_info = item.get('content', '')
                    
                    if draw_date:
                        results.append({
                            'issue': issue,
                            'red_balls': red_balls,
                            'draw_date': draw_date.strftime('%Y-%m-%d'),
                            'region': region_info
                        })
                        
                except (ValueError, KeyError, TypeError) as e:
                    logger.warning(f"解析单条快乐8数据失败：{e}, 数据：{item}")
                    continue
            
        except Exception as e:
            logger.error(f"解析快乐8 API 响应失败：{e}")
        
        return results
    
    def _parse_date(self, date_str: str) -> Optional[datetime]:
        date_formats = ['%Y-%m-%d', '%Y/%m/%d', '%Y年%m月%d日']
        
        for fmt in date_formats:
            try:
                return datetime.strptime(date_str, fmt)
            except ValueError:
                continue
        
        match = re.search(r'(\d{4})[-./](\d{1,2})[-./](\d{1,2})', date_str)
        if match:
            year, month, day = map(int, match.groups())
            return datetime(year, month, day)
        
        return None
    
    def fetch_latest_data(self) -> List[Dict]:
        logger.info("开始抓取快乐8最新数据...")
        
        params = {
            'name': 'kl8',
            'issueCount': '',
            'issueStart': '',
            'issueEnd': '',
            'dayStart': '',
            'dayEnd': '',
            'pageNo': 1,
            'pageSize': 30,
            'week': '',
            'systemType': 'PC'
        }
        
        json_data = self._fetch_json(API_BASE_URL, params=params)
        
        if json_data:
            results = self._parse_api_response(json_data)
            if results:
                logger.info(f"成功抓取 {len(results)} 条快乐8数据")
                
                # 立即存入数据库
                saved_count = 0
                for result in results:
                    if self.validate_result(result):
                        try:
                            kl8_db_manager.insert_lottery_result(result)
                            saved_count += 1
                        except Exception as e:
                            logger.error(f"保存快乐8数据失败: {result.get('issue')}, {e}")
                logger.info(f"已成功将 {saved_count} 条快乐8数据写入数据库")
                return results
        
        logger.error("快乐8最新数据抓取失败")
        return []
    
    def fetch_all_history(self, max_pages: int = 70) -> List[Dict]:
        logger.info("开始抓取快乐8所有历史数据...")
        all_results = []
        saved_count = 0
        current_page = 1
        
        while current_page <= max_pages:
            params = {
                'name': 'kl8',
                'issueCount': '',
                'issueStart': '',
                'issueEnd': '',
                'dayStart': '',
                'dayEnd': '',
                'pageNo': current_page,
                'pageSize': 30,
                'week': '',
                'systemType': 'PC'
            }
            
            logger.info(f"正在抓取快乐8第 {current_page} 页...")
            
            json_data = self._fetch_json(API_BASE_URL, params=params)
            
            if not json_data:
                logger.warning(f"快乐8第 {current_page} 页抓取失败")
                break
            
            page_results = self._parse_api_response(json_data)
            
            if not page_results:
                logger.info(f"快乐8第 {current_page} 页无更多数据")
                break
            
            all_results.extend(page_results)
            
            # 保存到数据库
            for result in page_results:
                if self.validate_result(result):
                    try:
                        kl8_db_manager.insert_lottery_result(result)
                        saved_count += 1
                    except Exception as e:
                        logger.warning(f"保存快乐8失败: {result.get('issue')}, {e}")
            
            logger.info(f"第 {current_page} 页已处理，当页共 {len(page_results)} 条，数据库累计保存 {saved_count} 条")
            
            if len(page_results) < 30:
                break
                
            current_page += 1
            time.sleep(REQUEST_DELAY)
            
        logger.info(f"快乐8历史数据抓取及落库完成，共计 {len(all_results)} 条，保存 {saved_count} 条")
        return all_results
    
    def validate_result(self, result: Dict) -> bool:
        try:
            if not result.get('issue') or not result['issue'].isdigit():
                return False
            
            red_balls = result.get('red_balls', [])
            if len(red_balls) != 20:
                return False
            for ball in red_balls:
                if not 1 <= ball <= 80:
                    return False
            
            if not result.get('draw_date'):
                return False
            
            return True
            
        except Exception as e:
            logger.error(f"快乐8数据验证出错：{e}")
            return False


kl8_crawler = KL8Crawler()


if __name__ == "__main__":
    test_crawler = KL8Crawler()
    results = test_crawler.fetch_latest_data()
    
    if results:
        print(f"\n成功抓取 {len(results)} 条数据")
        print("\n最新数据样例:")
        print(results[0])
    else:
        print("抓取失败")
