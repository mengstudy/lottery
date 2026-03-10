"""
统计计算模块
负责遗漏值计算、奇偶比分析、冷热号分组等
"""
import json
from typing import Dict, List, Tuple
from collections import Counter
from datetime import datetime

from .models import MissingStatistics, NumberAnalysis


class StatsCalculator:
    """统计计算器类"""
    
    def __init__(self):
        # 红球范围 1-33
        self.red_numbers = list(range(1, 34))
        # 蓝球范围 1-16
        self.blue_numbers = list(range(1, 17))
    
    def calculate_missing_values(self, lottery_results: List[Dict]) -> List[MissingStatistics]:
        """
        计算所有期次的遗漏值
        
        Args:
            lottery_results: 开奖结果列表（按期号升序排列）
            
        Returns:
            遗漏统计列表
        """
        if not lottery_results:
            return []
        
        # 初始化遗漏计数器
        red_missing_count = {num: 0 for num in self.red_numbers}
        blue_missing_count = {num: 0 for num in self.blue_numbers}
        
        missing_stats_list = []
        
        # 遍历每一期（从最早到最新）
        for result in lottery_results:
            # 兼容两种数据格式：转换后的格式（red_balls）或数据库原始格式（red_1, red_2...）
            if 'red_balls' in result and isinstance(result['red_balls'], list):
                current_red_balls = result['red_balls']
            else:
                # 数据库原始格式
                current_red_balls = [result.get(f'red_{i+1}', 0) for i in range(6)]
            
            # 兼容两种字段名：blue_ball 或 blue
            current_blue_ball = result.get('blue_ball', result.get('blue', 0))
            
            # 保存当前期的遗漏值（开奖前的状态）
            current_missing = MissingStatistics(
                issue=result['issue'],
                red_missing=red_missing_count.copy(),
                blue_missing=blue_missing_count.copy()
            )
            missing_stats_list.append(current_missing)
            
            # 更新遗漏计数
            # 对于开出的号码，遗漏值重置为 0；未开出的号码，遗漏值 +1
            for red_num in self.red_numbers:
                if red_num in current_red_balls:
                    red_missing_count[red_num] = 0
                else:
                    red_missing_count[red_num] += 1
            
            for blue_num in self.blue_numbers:
                if blue_num == current_blue_ball:
                    blue_missing_count[blue_num] = 0
                else:
                    blue_missing_count[blue_num] += 1
        
        return missing_stats_list
    
    def calculate_odd_even_ratio(self, red_balls: List[int]) -> str:
        """
        计算红球奇偶比
        
        Args:
            red_balls: 红球号码列表
            
        Returns:
            奇偶比字符串，如 "3:3"
        """
        odd_count = sum(1 for ball in red_balls if ball % 2 == 1)
        even_count = len(red_balls) - odd_count
        return f"{odd_count}:{even_count}"
    
    def analyze_issue(self, issue_result: Dict, missing_stat: MissingStatistics) -> NumberAnalysis:
        """
        分析单期号码的详细信息
        
        Args:
            issue_result: 该期开奖结果
            missing_stat: 该期遗漏统计
            
        Returns:
            号码分析结果
        """
        # 兼容两种数据格式：转换后的格式（red_balls）或数据库原始格式（red_1, red_2...）
        if 'red_balls' in issue_result and isinstance(issue_result['red_balls'], list):
            red_balls = issue_result['red_balls']
        else:
            # 数据库原始格式
            red_balls = [issue_result.get(f'red_{i+1}', 0) for i in range(6)]
        
        # 兼容两种字段名：blue_ball 或 blue
        blue_ball = issue_result.get('blue_ball', issue_result.get('blue', 0))
        
        # 获取开出号码的遗漏值
        red_missing_values = [missing_stat.red_missing[ball] for ball in red_balls]
        blue_missing_value = missing_stat.blue_missing[blue_ball]
        
        # 计算奇偶比
        red_odd_even = self.calculate_odd_even_ratio(red_balls)
        
        # 获取最大红球遗漏值
        max_red_missing = max(red_missing_values) if red_missing_values else 0
        
        # 根据最新遗漏值判断冷热号
        hot_numbers, cold_numbers = self.classify_hot_cold_numbers(missing_stat)
        
        # 计算遗漏次数分组（包含所有 33 个红球）
        missing_groups = self.calculate_all_red_missing_groups(missing_stat)
        
        # 计算开出号码的遗漏次数分组（仅本期开出的 6 个红球）
        drawn_ball_missing_groups = self.calculate_missing_groups(red_balls, red_missing_values)
        
        return NumberAnalysis(
            issue=issue_result['issue'],
            draw_date=issue_result['draw_date'],
            red_balls=red_balls,
            blue_ball=blue_ball,
            red_odd_even=red_odd_even,
            red_missing_values=red_missing_values,
            blue_missing_value=blue_missing_value,
            max_red_missing=max_red_missing,
            hot_numbers=hot_numbers,
            cold_numbers=cold_numbers,
            missing_groups=missing_groups,
            drawn_ball_missing_groups=drawn_ball_missing_groups
        )
    
    def classify_hot_cold_numbers(self, missing_stat: MissingStatistics) -> Tuple[List[int], List[int]]:
        """
        分类冷热号
        
        Args:
            missing_stat: 遗漏统计对象
            
        Returns:
            (热号列表，冷号列表)
        """
        hot_numbers = []
        cold_numbers = []
        
        # 红球冷热号分类
        for num in self.red_numbers:
            missing = missing_stat.red_missing[num]
            if missing == 0:
                hot_numbers.append(num)  # 热号：刚开出
            elif missing > 10:
                cold_numbers.append(num)  # 大冷号：遗漏超过 10 期
        
        return hot_numbers, cold_numbers
    
    def calculate_missing_groups(self, red_balls: List[int], red_missing_values: List[int]) -> Dict[int, List[int]]:
        """
        计算遗漏次数分组（针对本期开出的红球）
        
        Args:
            red_balls: 本期开出的红球号码列表
            red_missing_values: 本期开出的红球对应的遗漏值
            
        Returns:
            遗漏次数分组字典 {遗漏次数：[红球号码]}
            例如：{0: [5, 12], 1: [3], 2: [8], 9+: [15, 20, 28]}
        """
        groups = {}
        
        for ball, missing in zip(red_balls, red_missing_values):
            # 将遗漏次数 >= 9 的归为一组
            group_key = missing if missing < 9 else 9
            
            if group_key not in groups:
                groups[group_key] = []
            groups[group_key].append(ball)
        
        # 按遗漏次数排序
        sorted_groups = dict(sorted(groups.items()))
        
        return sorted_groups
    
    def calculate_all_red_missing_groups(self, missing_stat: MissingStatistics) -> Dict[int, List[int]]:
        """
        计算所有 33 个红球的遗漏次数分组
        
        Args:
            missing_stat: 遗漏统计对象
            
        Returns:
            遗漏次数分组字典 {遗漏次数：[红球号码]}
            例如：{0: [5, 12, 28], 1: [3, 15], 2: [8, 20], ..., 9+: [1, 7, 14, ...]}
        """
        groups = {}
        
        # 遍历所有 33 个红球
        for num in self.red_numbers:
            missing = missing_stat.red_missing[num]
            # 将遗漏次数 >= 9 的归为一组
            group_key = missing if missing < 9 else 9
            
            if group_key not in groups:
                groups[group_key] = []
            groups[group_key].append(num)
        
        # 按遗漏次数排序
        sorted_groups = dict(sorted(groups.items()))
        
        return sorted_groups
    
    def get_current_missing_ranking(self, latest_missing: MissingStatistics) -> Dict:
        """
        获取当前遗漏值排行榜
        
        Args:
            latest_missing: 最新一期的遗漏统计
            
        Returns:
            包含红球和蓝球遗漏排行的字典
        """
        # 红球遗漏排行（按遗漏值降序）
        red_ranking = sorted(
            [(num, missing) for num, missing in latest_missing.red_missing.items()],
            key=lambda x: x[1],
            reverse=True
        )
        
        # 蓝球遗漏排行（按遗漏值降序）
        blue_ranking = sorted(
            [(num, missing) for num, missing in latest_missing.blue_missing.items()],
            key=lambda x: x[1],
            reverse=True
        )
        
        return {
            'red_ranking': red_ranking,
            'blue_ranking': blue_ranking
        }
    
    def get_number_frequency(self, lottery_results: List[Dict]) -> Dict:
        """
        统计号码出现频率
        
        Args:
            lottery_results: 历史开奖数据
            
        Returns:
            号码频率统计
        """
        red_counter = Counter()
        blue_counter = Counter()
        
        for result in lottery_results:
            red_balls = result['red_balls'] if isinstance(result['red_balls'], list) else \
                       [result[f'red_{i+1}'] for i in range(6)]
            blue_ball = result['blue']
            
            red_counter.update(red_balls)
            blue_counter.update([blue_ball])
        
        return {
            'red_frequency': dict(red_counter),
            'blue_frequency': dict(blue_counter)
        }
    
    def get_missing_trend(self, number: int, missing_stats: List[MissingStatistics], is_blue: bool = False) -> List[int]:
        """
        获取指定号码的遗漏走势
        
        Args:
            number: 号码
            missing_stats: 遗漏统计列表
            is_blue: 是否为蓝球
            
        Returns:
            遗漏值变化列表
        """
        trend = []
        for stat in missing_stats:
            if is_blue:
                trend.append(stat.blue_missing.get(number, 0))
            else:
                trend.append(stat.red_missing.get(number, 0))
        return trend


# 单例模式
calculator = StatsCalculator()


if __name__ == "__main__":
    # 测试代码
    test_data = [
        {'issue': '2024001', 'red_balls': [1, 5, 10, 15, 20, 25], 'blue': 8, 'draw_date': '2024-01-01'},
        {'issue': '2024002', 'red_balls': [2, 6, 11, 16, 21, 26], 'blue': 9, 'draw_date': '2024-01-02'},
        {'issue': '2024003', 'red_balls': [3, 7, 12, 17, 22, 27], 'blue': 10, 'draw_date': '2024-01-03'},
    ]
    
    calc = StatsCalculator()
    results = calc.calculate_missing_values(test_data)
    
    print("遗漏值计算完成:")
    for result in results:
        print(f"期号：{result.issue}, 红球遗漏：{sum(result.red_missing.values())}")
