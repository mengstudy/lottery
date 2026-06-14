"""
快乐8统计计算模块
负责遗漏值计算、奇偶比分析、冷热号分组等
"""
from dataclasses import dataclass, field
from typing import Dict, List, Tuple


@dataclass
class MissingStatistics:
    issue: str
    red_missing: Dict[int, int]


@dataclass
class NumberAnalysis:
    issue: str
    draw_date: str
    red_balls: List[int]
    red_odd_even: str
    red_sum: int
    red_missing_values: List[int]
    max_red_missing: int
    hot_numbers: List[int]
    cold_numbers: List[int]
    missing_groups: Dict[int, List[int]] = field(default_factory=dict)
    
    def to_dict(self) -> Dict:
        missing_groups_str = {str(k): v for k, v in self.missing_groups.items()}
        return {
            'issue': self.issue,
            'draw_date': self.draw_date,
            'red_balls': self.red_balls,
            'red_odd_even': self.red_odd_even,
            'red_sum': self.red_sum,
            'red_missing_values': self.red_missing_values,
            'max_red_missing': self.max_red_missing,
            'hot_numbers': self.hot_numbers,
            'cold_numbers': self.cold_numbers,
            'missing_groups': missing_groups_str
        }


class KL8Calculator:
    """快乐8统计计算器类"""
    
    def __init__(self):
        self.red_numbers = list(range(1, 81))
    
    def calculate_missing_values(self, lottery_results: List[Dict]) -> List[MissingStatistics]:
        """
        根据开奖记录计算每一期的遗漏值
        """
        if not lottery_results:
            return []
        
        red_missing_count = {num: 0 for num in self.red_numbers}
        missing_stats_list = []
        
        for result in lottery_results:
            if 'red_balls' in result and isinstance(result['red_balls'], list):
                current_red_balls = result['red_balls']
            else:
                current_red_balls = [result.get(f'red_{i+1}', 0) for i in range(20)]
            
            # 保存当前开奖前的遗漏值
            current_missing = MissingStatistics(
                issue=result['issue'],
                red_missing=red_missing_count.copy()
            )
            missing_stats_list.append(current_missing)
            
            # 更新下一期的遗漏值
            for red_num in self.red_numbers:
                if red_num in current_red_balls:
                    red_missing_count[red_num] = 0
                else:
                    red_missing_count[red_num] += 1
        
        return missing_stats_list
    
    def calculate_odd_even_ratio(self, red_balls: List[int]) -> str:
        """计算奇偶比"""
        odd_count = sum(1 for ball in red_balls if ball % 2 == 1)
        even_count = len(red_balls) - odd_count
        return f"{odd_count}:{even_count}"
    
    def calculate_sum(self, balls: List[int]) -> int:
        """计算和值"""
        return sum(balls)
    
    def analyze_issue(self, issue_result: Dict, missing_stat: MissingStatistics) -> NumberAnalysis:
        """
        分析单期开奖的统计指标
        """
        if 'red_balls' in issue_result and isinstance(issue_result['red_balls'], list):
            red_balls = issue_result['red_balls']
        else:
            red_balls = [issue_result.get(f'red_{i+1}', 0) for i in range(20)]
            
        red_missing_values = [missing_stat.red_missing.get(ball, 0) for ball in red_balls]
        
        red_odd_even = self.calculate_odd_even_ratio(red_balls)
        red_sum = self.calculate_sum(red_balls)
        
        max_red_missing = max(red_missing_values) if red_missing_values else 0
        
        hot_numbers, cold_numbers = self.classify_hot_cold_numbers(missing_stat)
        missing_groups = self.calculate_all_red_missing_groups(missing_stat)
        
        return NumberAnalysis(
            issue=issue_result['issue'],
            draw_date=issue_result['draw_date'],
            red_balls=red_balls,
            red_odd_even=red_odd_even,
            red_sum=red_sum,
            red_missing_values=red_missing_values,
            max_red_missing=max_red_missing,
            hot_numbers=hot_numbers,
            cold_numbers=cold_numbers,
            missing_groups=missing_groups
        )
    
    def classify_hot_cold_numbers(self, missing_stat: MissingStatistics) -> Tuple[List[int], List[int]]:
        """
        划分冷热号码
        """
        hot_numbers = []
        cold_numbers = []
        
        for num in self.red_numbers:
            missing = missing_stat.red_missing.get(num, 0)
            if missing == 0:
                hot_numbers.append(num)
            elif missing > 15: # 快乐8共有80个号码，冷号标准设为超过15期未出
                cold_numbers.append(num)
        
        return hot_numbers, cold_numbers
    
    def calculate_all_red_missing_groups(self, missing_stat: MissingStatistics) -> Dict[int, List[int]]:
        """
        根据遗漏值进行号码分组
        """
        groups = {}
        
        for num in self.red_numbers:
            missing = missing_stat.red_missing.get(num, 0)
            group_key = missing if missing < 9 else 9
            
            if group_key not in groups:
                groups[group_key] = []
            groups[group_key].append(num)
        
        return dict(sorted(groups.items()))
    
    def get_current_missing_ranking(self, latest_missing: MissingStatistics) -> Dict:
        """
        获取红球遗漏排行榜
        """
        red_ranking = sorted(
            [(num, missing) for num, missing in latest_missing.red_missing.items()],
            key=lambda x: x[1],
            reverse=True
        )
        
        return {
            'red_ranking': red_ranking
        }
    
    def calculate_all_missing_for_issue(self, lottery_results: List[Dict], target_index: int) -> Tuple[Dict[int, int], None]:
        """
        单独计算某期的遗漏数据
        """
        if not lottery_results or target_index < 0 or target_index >= len(lottery_results):
            return {}, None
        
        red_missing_count = {num: 0 for num in self.red_numbers}
        
        for i in range(target_index + 1):
            result = lottery_results[i]
            
            if 'red_balls' in result and isinstance(result['red_balls'], list):
                current_red_balls = result['red_balls']
            else:
                current_red_balls = [result.get(f'red_{i+1}', 0) for i in range(20)]
            
            # 如果是目标期，则记录当前的遗漏（未包含本期的开奖号码清除为0的动作）
            # 注意：遗漏计算流程在计算当前期前，号码历史遗漏即为我们要统计的值
            if i == target_index:
                break
                
            for red_num in self.red_numbers:
                if red_num in current_red_balls:
                    red_missing_count[red_num] = 0
                else:
                    red_missing_count[red_num] += 1
        
        return red_missing_count.copy(), None


kl8_calculator = KL8Calculator()


if __name__ == "__main__":
    test_data = [
        {'issue': '2026001', 'red_balls': list(range(1, 21)), 'draw_date': '2026-01-01'},
        {'issue': '2026002', 'red_balls': list(range(2, 22)), 'draw_date': '2026-01-02'},
    ]
    calc = KL8Calculator()
    results = calc.calculate_missing_values(test_data)
    print("计算完毕：")
    for r in results:
        print(f"期号: {r.issue}, 号码1遗漏: {r.red_missing[1]}, 号码21遗漏: {r.red_missing[21]}")
