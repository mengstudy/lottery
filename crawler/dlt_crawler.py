"""
大乐透爬虫模块
负责抓取体彩网数据并解析开奖结果
"""
import requests
import time
import logging
import re
from datetime import datetime
from typing import List, Dict, Optional
from pathlib import Path


HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
    'Accept': 'application/json, text/plain, */*',
    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
    'Accept-Encoding': 'gzip, deflate, br',
    'Referer': 'https://www.lottery.gov.cn/',
    'Origin': 'https://www.lottery.gov.cn',
}

API_BASE_URL = "https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry"
REQUEST_DELAY = 2.0
TIMEOUT = 10
MAX_RETRIES = 3
RETRY_DELAY = 3.0

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(Path(__file__).parent.parent / 'dlt_crawler.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


class DLTCrawler:
    """大乐透爬虫类"""
    
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update(HEADERS)
        self.game_no = "85"
        
    def _fetch_json(self, url: str, params: dict = None, retry_count: int = 0) -> Optional[dict]:
        try:
            response = self.session.get(url, params=params, timeout=TIMEOUT)
            response.raise_for_status()
            time.sleep(REQUEST_DELAY)
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
        results = []
        
        try:
            if not json_data or 'value' not in json_data:
                logger.warning("API 返回数据格式异常")
                return results
            
            data = json_data.get('value', {}).get('list', [])
            
            for item in data:
                try:
                    issue = item.get('lotteryDrawNum', '')
                    if not issue or not re.match(r'\d+', issue):
                        continue
                    
                    red_ball_str = item.get('lotteryDrawResult', '')
                    if not red_ball_str:
                        continue
                    
                    balls = red_ball_str.split(' ')  # 用空格分隔
                    if len(balls) != 7:
                        continue
                    
                    red_balls = [int(x.strip()) for x in balls[:5]]
                    blue_balls = [int(x.strip()) for x in balls[5:]]
                    
                    if len(red_balls) != 5 or len(blue_balls) != 2:
                        continue
                    
                    date_str = item.get('lotteryDrawTime', '')
                    if not date_str:
                        continue
                    
                    draw_date = self._parse_date(date_str)
                    
                    pool_money = item.get('poolMoney', '')
                    
                    if draw_date:
                        results.append({
                            'issue': issue,
                            'red_balls': red_balls,
                            'blue_balls': blue_balls,
                            'draw_date': draw_date.strftime('%Y-%m-%d'),
                            'region': pool_money
                        })
                        
                except (ValueError, KeyError, TypeError) as e:
                    logger.warning(f"解析单条数据失败：{e}, 数据：{item}")
                    continue
            
        except Exception as e:
            logger.error(f"解析 API 响应失败：{e}")
        
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
        logger.info("开始抓取大乐透最新数据...")
        
        params = {
            'gameNo': self.game_no,
            'provinceId': 0,
            'pageSize': 30,
            'isVerify': 1,
            'pageNo': 1
        }
        
        json_data = self._fetch_json(API_BASE_URL, params=params)
        
        if json_data:
            results = self._parse_api_response(json_data)
            if results:
                logger.info(f"成功抓取 {len(results)} 条大乐透数据")
                return results
        
        logger.error("大乐透数据抓取失败")
        return []
    
    def fetch_all_history(self, max_pages: int = 150) -> List[Dict]:
        from database.dlt_db_manager import dlt_db_manager
        
        logger.info("开始抓取大乐透所有历史数据...")
        all_results = []
        saved_count = 0
        current_page = 1
        
        while current_page <= max_pages:
            params = {
                'gameNo': self.game_no,
                'provinceId': 0,
                'pageSize': 30,
                'isVerify': 1,
                'pageNo': current_page
            }
            
            logger.info(f"正在抓取第 {current_page} 页...")
            
            json_data = self._fetch_json(API_BASE_URL, params=params)
            
            if not json_data:
                logger.warning(f"第 {current_page} 页抓取失败")
                break
            
            page_results = self._parse_api_response(json_data)
            
            if not page_results:
                logger.info(f"第 {current_page} 页没有更多数据")
                break
            
            all_results.extend(page_results)
            
            # 边抓取边保存
            for result in page_results:
                if self.validate_result(result):
                    try:
                        dlt_db_manager.insert_lottery_result(result)
                        saved_count += 1
                    except Exception as e:
                        logger.warning(f"保存失败: {result.get('issue')}, {e}")
            
            logger.info(f"第 {current_page} 页成功抓取 {len(page_results)} 条，已保存 {saved_count} 条")
            
            if len(page_results) < 30:
                break
            
            current_page += 1
        
        logger.info(f"历史数据抓取完成，共抓取 {len(all_results)} 条，保存 {saved_count} 条")
        return all_results
    
    def validate_result(self, result: Dict) -> bool:
        try:
            if not result.get('issue') or not result['issue'].isdigit():
                return False
            
            red_balls = result.get('red_balls', [])
            if len(red_balls) != 5:
                return False
            for ball in red_balls:
                if not 1 <= ball <= 35:
                    return False
            
            blue_balls = result.get('blue_balls', [])
            if len(blue_balls) != 2:
                return False
            for ball in blue_balls:
                if not 1 <= ball <= 12:
                    return False
            
            if not result.get('draw_date'):
                return False
            
            return True
            
        except Exception as e:
            logger.error(f"数据验证失败：{e}")
            return False


dlt_crawler = DLTCrawler()


if __name__ == "__main__":
    test_crawler = DLTCrawler()
    results = test_crawler.fetch_latest_data()
    
    if results:
        print(f"\n成功抓取 {len(results)} 条大乐透数据")
        print("\n最新一期数据:")
        print(results[0])
    else:
        print("抓取失败")
