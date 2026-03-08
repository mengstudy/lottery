"""
爬虫配置模块
配置目标 URL、请求头、反爬策略等
"""

# 中国福彩网官方数据源 - API 接口
API_BASE_URL = "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice"
API_PARAMS = {
    'name': 'ssq',
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

# 中国福彩网官方数据源 - 网页版（备用）
BASE_URL = "https://www.cwl.gov.cn/ygkj/wqkjgg/ssq/"

# 备选数据源（500 彩票网）
BACKUP_BASE_URL = "http://datachart.500.com/ssq/history/"

# 请求头配置
HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
    'Accept-Encoding': 'gzip, deflate',
    'Connection': 'keep-alive',
    'Upgrade-Insecure-Requests': '1',
    'Cache-Control': 'max-age=0',
}

# 请求间隔（秒），避免过快请求被封禁
REQUEST_DELAY = 2.0

# 超时时间（秒）
TIMEOUT = 10

# 最大重试次数
MAX_RETRIES = 3

# 重试间隔（秒）
RETRY_DELAY = 3.0

# 日志配置
LOG_FORMAT = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
LOG_FILE = 'crawler.log'
