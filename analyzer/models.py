"""
数据模型定义模块
定义开奖记录和遗漏统计的数据结构
"""
from dataclasses import dataclass, field
from datetime import datetime
from typing import List, Dict


@dataclass
class LotteryResult:
    """开奖记录模型"""
    issue: str  # 期号
    red_balls: List[int]  # 红球号码 (6 个)
    blue_ball: int  # 蓝球号码 (1 个)
    draw_date: datetime  # 开奖日期
    
    def __post_init__(self):
        """验证数据有效性"""
        if len(self.red_balls) != 6:
            raise ValueError("红球号码必须是 6 个")
        
        for ball in self.red_balls:
            if not 1 <= ball <= 33:
                raise ValueError(f"红球号码 {ball} 超出范围 (1-33)")
        
        if not 1 <= self.blue_ball <= 16:
            raise ValueError(f"蓝球号码 {self.blue_ball} 超出范围 (1-16)")
    
    def to_dict(self) -> Dict:
        """转换为字典格式"""
        return {
            'issue': self.issue,
            'red_1': self.red_balls[0],
            'red_2': self.red_balls[1],
            'red_3': self.red_balls[2],
            'red_4': self.red_balls[3],
            'red_5': self.red_balls[4],
            'red_6': self.red_balls[5],
            'blue': self.blue_ball,
            'draw_date': self.draw_date.strftime('%Y-%m-%d') if isinstance(self.draw_date, datetime) else self.draw_date
        }
    
    @classmethod
    def from_db_row(cls, row: Dict) -> 'LotteryResult':
        """从数据库行创建实例"""
        return cls(
            issue=row['issue'],
            red_balls=[row['red_1'], row['red_2'], row['red_3'], 
                      row['red_4'], row['red_5'], row['red_6']],
            blue_ball=row['blue'],
            draw_date=datetime.strptime(row['draw_date'], '%Y-%m-%d')
        )


@dataclass
class MissingStatistics:
    """遗漏统计模型"""
    issue: str  # 期号
    red_missing: Dict[int, int]  # 红球遗漏值 {号码：遗漏期数}
    blue_missing: Dict[int, int]  # 蓝球遗漏值 {号码：遗漏期数}
    calculated_at: datetime = field(default_factory=datetime.now)
    
    def to_dict(self) -> Dict:
        """转换为字典格式"""
        return {
            'issue': self.issue,
            'red_missing': self.red_missing,
            'blue_missing': self.blue_missing,
            'calculated_at': self.calculated_at.isoformat()
        }
    
    @classmethod
    def from_db_row(cls, row: Dict) -> 'MissingStatistics':
        """从数据库行创建实例"""
        import json
        return cls(
            issue=row['issue'],
            red_missing={int(k): v for k, v in json.loads(row['red_missing']).items()},
            blue_missing={int(k): v for k, v in json.loads(row['blue_missing']).items()},
            calculated_at=datetime.fromisoformat(row['calculated_at']) if row.get('calculated_at') else datetime.now()
        )


@dataclass
class NumberAnalysis:
    """单期号码分析结果"""
    issue: str
    draw_date: str
    red_balls: List[int]
    blue_ball: int
    red_odd_even: str  # 红球奇偶比，如 "3:3"
    red_missing_values: List[int]  # 本期开出的红球的遗漏值
    blue_missing_value: int  # 本期开出的蓝球的遗漏值
    max_red_missing: int  # 红球最大遗漏值
    hot_numbers: List[int]  # 热号列表
    cold_numbers: List[int]  # 冷号列表
    
    def to_dict(self) -> Dict:
        """转换为字典格式"""
        return {
            'issue': self.issue,
            'draw_date': self.draw_date,
            'red_balls': self.red_balls,
            'blue_ball': self.blue_ball,
            'red_odd_even': self.red_odd_even,
            'red_missing_values': self.red_missing_values,
            'blue_missing_value': self.blue_missing_value,
            'max_red_missing': self.max_red_missing,
            'hot_numbers': self.hot_numbers,
            'cold_numbers': self.cold_numbers
        }
