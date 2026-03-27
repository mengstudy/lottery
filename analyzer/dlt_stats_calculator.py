"""
大乐透统计计算模块
负责遗漏值计算、奇偶比分析、冷热号分组等
"""
from dataclasses import dataclass, field
from typing import Dict, List, Tuple


@dataclass
class MissingStatistics:
    issue: str
    red_missing: Dict[int, int]
    blue_missing: Dict[int, int]


@dataclass
class NumberAnalysis:
    issue: str
    draw_date: str
    red_balls: List[int]
    blue_balls: List[int]
    red_odd_even: str
    red_sum: int
    blue_sum: int
    red_missing_values: List[int]
    blue_missing_values: List[int]
    max_red_missing: int
    max_blue_missing: int
    hot_numbers: List[int]
    cold_numbers: List[int]
    missing_groups: Dict[int, List[int]] = field(default_factory=dict)
    
    def to_dict(self) -> Dict:
        missing_groups_str = {str(k): v for k, v in self.missing_groups.items()}
        return {
            'issue': self.issue,
            'draw_date': self.draw_date,
            'red_balls': self.red_balls,
            'blue_balls': self.blue_balls,
            'red_odd_even': self.red_odd_even,
            'red_sum': self.red_sum,
            'blue_sum': self.blue_sum,
            'red_missing_values': self.red_missing_values,
            'blue_missing_values': self.blue_missing_values,
            'max_red_missing': self.max_red_missing,
            'max_blue_missing': self.max_blue_missing,
            'hot_numbers': self.hot_numbers,
            'cold_numbers': self.cold_numbers,
            'missing_groups': missing_groups_str
        }


class DLTCalculator:
    """大乐透统计计算器类"""
    
    def __init__(self):
        self.red_numbers = list(range(1, 36))
        self.blue_numbers = list(range(1, 13))
    
    def calculate_missing_values(self, lottery_results: List[Dict]) -> List[MissingStatistics]:
        if not lottery_results:
            return []
        
        red_missing_count = {num: 0 for num in self.red_numbers}
        blue_missing_count = {num: 0 for num in self.blue_numbers}
        
        missing_stats_list = []
        
        for result in lottery_results:
            if 'red_balls' in result and isinstance(result['red_balls'], list):
                current_red_balls = result['red_balls']
            else:
                current_red_balls = [result.get(f'red_{i+1}', 0) for i in range(5)]
            
            if 'blue_balls' in result and isinstance(result['blue_balls'], list):
                current_blue_balls = result['blue_balls']
            else:
                current_blue_balls = [result.get('blue_1', 0), result.get('blue_2', 0)]
            
            current_missing = MissingStatistics(
                issue=result['issue'],
                red_missing=red_missing_count.copy(),
                blue_missing=blue_missing_count.copy()
            )
            missing_stats_list.append(current_missing)
            
            for red_num in self.red_numbers:
                if red_num in current_red_balls:
                    red_missing_count[red_num] = 0
                else:
                    red_missing_count[red_num] += 1
            
            for blue_num in self.blue_numbers:
                if blue_num in current_blue_balls:
                    blue_missing_count[blue_num] = 0
                else:
                    blue_missing_count[blue_num] += 1
        
        return missing_stats_list
    
    def calculate_odd_even_ratio(self, red_balls: List[int]) -> str:
        odd_count = sum(1 for ball in red_balls if ball % 2 == 1)
        even_count = len(red_balls) - odd_count
        return f"{odd_count}:{even_count}"
    
    def calculate_sum(self, balls: List[int]) -> int:
        return sum(balls)
    
    def analyze_issue(self, issue_result: Dict, missing_stat: MissingStatistics) -> NumberAnalysis:
        if 'red_balls' in issue_result and isinstance(issue_result['red_balls'], list):
            red_balls = issue_result['red_balls']
        else:
            red_balls = [issue_result.get(f'red_{i+1}', 0) for i in range(5)]
        
        if 'blue_balls' in issue_result and isinstance(issue_result['blue_balls'], list):
            blue_balls = issue_result['blue_balls']
        else:
            blue_balls = [issue_result.get('blue_1', 0), issue_result.get('blue_2', 0)]
        
        red_missing_values = [missing_stat.red_missing[ball] for ball in red_balls]
        blue_missing_values = [missing_stat.blue_missing[ball] for ball in blue_balls]
        
        red_odd_even = self.calculate_odd_even_ratio(red_balls)
        red_sum = self.calculate_sum(red_balls)
        blue_sum = self.calculate_sum(blue_balls)
        
        max_red_missing = max(red_missing_values) if red_missing_values else 0
        max_blue_missing = max(blue_missing_values) if blue_missing_values else 0
        
        hot_numbers, cold_numbers = self.classify_hot_cold_numbers(missing_stat)
        missing_groups = self.calculate_all_red_missing_groups(missing_stat)
        
        return NumberAnalysis(
            issue=issue_result['issue'],
            draw_date=issue_result['draw_date'],
            red_balls=red_balls,
            blue_balls=blue_balls,
            red_odd_even=red_odd_even,
            red_sum=red_sum,
            blue_sum=blue_sum,
            red_missing_values=red_missing_values,
            blue_missing_values=blue_missing_values,
            max_red_missing=max_red_missing,
            max_blue_missing=max_blue_missing,
            hot_numbers=hot_numbers,
            cold_numbers=cold_numbers,
            missing_groups=missing_groups
        )
    
    def classify_hot_cold_numbers(self, missing_stat: MissingStatistics) -> Tuple[List[int], List[int]]:
        hot_numbers = []
        cold_numbers = []
        
        for num in self.red_numbers:
            missing = missing_stat.red_missing[num]
            if missing == 0:
                hot_numbers.append(num)
            elif missing > 12:
                cold_numbers.append(num)
        
        return hot_numbers, cold_numbers
    
    def calculate_all_red_missing_groups(self, missing_stat: MissingStatistics) -> Dict[int, List[int]]:
        groups = {}
        
        for num in self.red_numbers:
            missing = missing_stat.red_missing[num]
            group_key = missing if missing < 9 else 9
            
            if group_key not in groups:
                groups[group_key] = []
            groups[group_key].append(num)
        
        return dict(sorted(groups.items()))
    
    def get_current_missing_ranking(self, latest_missing: MissingStatistics) -> Dict:
        red_ranking = sorted(
            [(num, missing) for num, missing in latest_missing.red_missing.items()],
            key=lambda x: x[1],
            reverse=True
        )
        
        blue_ranking = sorted(
            [(num, missing) for num, missing in latest_missing.blue_missing.items()],
            key=lambda x: x[1],
            reverse=True
        )
        
        return {
            'red_ranking': red_ranking,
            'blue_ranking': blue_ranking
        }
    
    def calculate_all_missing_for_issue(self, lottery_results: List[Dict], target_index: int) -> Tuple[Dict[int, int], Dict[int, int]]:
        if not lottery_results or target_index < 0 or target_index >= len(lottery_results):
            return {}, {}
        
        red_missing_count = {num: 0 for num in self.red_numbers}
        blue_missing_count = {num: 0 for num in self.blue_numbers}
        
        for i in range(target_index + 1):
            result = lottery_results[i]
            
            if 'red_balls' in result and isinstance(result['red_balls'], list):
                current_red_balls = result['red_balls']
            else:
                current_red_balls = [result.get(f'red_{i+1}', 0) for i in range(5)]
            
            if 'blue_balls' in result and isinstance(result['blue_balls'], list):
                current_blue_balls = result['blue_balls']
            else:
                current_blue_balls = [result.get('blue_1', 0), result.get('blue_2', 0)]
            
            for red_num in self.red_numbers:
                if red_num in current_red_balls:
                    red_missing_count[red_num] = 0
                else:
                    red_missing_count[red_num] += 1
            
            for blue_num in self.blue_numbers:
                if blue_num in current_blue_balls:
                    blue_missing_count[blue_num] = 0
                else:
                    blue_missing_count[blue_num] += 1
        
        return red_missing_count.copy(), blue_missing_count.copy()


dlt_calculator = DLTCalculator()


if __name__ == "__main__":
    test_data = [
        {'issue': '25001', 'red_balls': [1, 5, 10, 15, 20], 'blue_balls': [3, 8], 'draw_date': '2025-01-01'},
        {'issue': '25002', 'red_balls': [2, 6, 11, 16, 21], 'blue_balls': [4, 9], 'draw_date': '2025-01-04'},
    ]
    
    calc = DLTCalculator()
    results = calc.calculate_missing_values(test_data)
    
    print("遗漏值计算完成:")
    for result in results:
        print(f"期号：{result.issue}, 红球遗漏：{sum(result.red_missing.values())}")
