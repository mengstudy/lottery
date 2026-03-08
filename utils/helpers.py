"""
工具函数模块
提供日期格式化、数字补零等辅助功能
"""
from datetime import datetime
import re


def format_date(date_obj, fmt='%Y-%m-%d'):
    """
    格式化日期对象
    
    Args:
        date_obj: datetime 对象或日期字符串
        fmt: 输出格式，默认 '%Y-%m-%d'
        
    Returns:
        格式化后的日期字符串
    """
    if isinstance(date_obj, str):
        date_obj = parse_date(date_obj)
    
    if isinstance(date_obj, datetime):
        return date_obj.strftime(fmt)
    
    return None


def parse_date(date_str):
    """
    解析日期字符串
    
    Args:
        date_str: 日期字符串
        
    Returns:
        datetime 对象，失败返回 None
    """
    if not date_str:
        return None
    
    # 常见的日期格式
    formats = [
        '%Y-%m-%d',
        '%Y/%m/%d',
        '%Y年%m月%d日',
        '%y-%m-%d',
        '%y/%m/%d',
        '%Y-%m-%d %H:%M:%S',
        '%Y/%m/%d %H:%M:%S'
    ]
    
    for fmt in formats:
        try:
            return datetime.strptime(date_str, fmt)
        except ValueError:
            continue
    
    # 尝试从字符串中提取数字
    match = re.search(r'(\d{4})[-./](\d{1,2})[-./](\d{1,2})', date_str)
    if match:
        year, month, day = map(int, match.groups())
        try:
            return datetime(year, month, day)
        except ValueError:
            pass
    
    return None


def pad_number(number, width=2):
    """
    数字补零
    
    Args:
        number: 数字
        width: 宽度，默认 2
        
    Returns:
        补零后的字符串
    """
    return str(number).zfill(width)


def validate_red_ball(ball):
    """
    验证红球号码
    
    Args:
        ball: 红球号码
        
    Returns:
        是否有效
    """
    try:
        ball_num = int(ball)
        return 1 <= ball_num <= 33
    except (ValueError, TypeError):
        return False


def validate_blue_ball(ball):
    """
    验证蓝球号码
    
    Args:
        ball: 蓝球号码
        
    Returns:
        是否有效
    """
    try:
        ball_num = int(ball)
        return 1 <= ball_num <= 16
    except (ValueError, TypeError):
        return False


def sort_balls(balls):
    """
    对号码排序
    
    Args:
        balls: 号码列表
        
    Returns:
        排序后的列表
    """
    return sorted(balls)


def calculate_sum(balls):
    """
    计算号码和
    
    Args:
        balls: 号码列表
        
    Returns:
        号码和
    """
    return sum(balls)


def calculate_ac_index(balls):
    """
    计算 AC 指数（数字复杂指数）
    
    Args:
        balls: 号码列表
        
    Returns:
        AC 指数
    """
    if len(balls) < 2:
        return 0
    
    # 计算所有两两差值
    diffs = set()
    for i in range(len(balls)):
        for j in range(i + 1, len(balls)):
            diffs.add(abs(balls[i] - balls[j]))
    
    # AC = 差值种类数 - (号码数 - 1)
    ac = len(diffs) - (len(balls) - 1)
    return ac


def get_ball_range(balls):
    """
    获取号码跨度（最大值 - 最小值）
    
    Args:
        balls: 号码列表
        
    Returns:
        跨度
    """
    if not balls:
        return 0
    return max(balls) - min(balls)


if __name__ == "__main__":
    # 测试工具函数
    print("日期格式化测试:")
    now = datetime.now()
    print(f"当前日期：{format_date(now)}")
    print(f"补零测试：{pad_number(5)}")
    print(f"验证红球：{validate_red_ball(15)}")
    print(f"验证蓝球：{validate_blue_ball(8)}")
