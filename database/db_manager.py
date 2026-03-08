"""
数据库管理模块
负责 SQLite 数据库的连接、初始化和基本操作
"""
import sqlite3
from pathlib import Path
from datetime import datetime


class DatabaseManager:
    """数据库管理类"""
    
    def __init__(self, db_path="data/lottery.db"):
        """
        初始化数据库连接
        
        Args:
            db_path: 数据库文件路径
        """
        # 确保使用绝对路径
        base_dir = Path(__file__).parent.parent
        self.db_path = base_dir / db_path
        self.conn = None
        
    def connect(self):
        """建立数据库连接"""
        try:
            self.conn = sqlite3.connect(str(self.db_path))
            self.conn.row_factory = sqlite3.Row  # 使结果可以按列名访问
            return self.conn
        except sqlite3.Error as e:
            print(f"数据库连接错误：{e}")
            raise
    
    def close(self):
        """关闭数据库连接"""
        if self.conn:
            self.conn.close()
            self.conn = None
    
    def initialize(self):
        """初始化数据库表结构"""
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            # 创建开奖结果表
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS lottery_results (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    issue TEXT UNIQUE NOT NULL,
                    red_1 INTEGER NOT NULL,
                    red_2 INTEGER NOT NULL,
                    red_3 INTEGER NOT NULL,
                    red_4 INTEGER NOT NULL,
                    red_5 INTEGER NOT NULL,
                    red_6 INTEGER NOT NULL,
                    blue INTEGER NOT NULL,
                    draw_date DATE NOT NULL,
                    region TEXT
                )
            ''')
            
            # 创建遗漏值统计表（用于缓存每期的遗漏值）
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS missing_stats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    issue TEXT NOT NULL,
                    red_missing TEXT NOT NULL,  -- JSON 格式存储红球遗漏值
                    blue_missing TEXT NOT NULL,  -- JSON 格式存储蓝球遗漏值
                    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            # 创建索引以提高查询性能
            cursor.execute('CREATE INDEX IF NOT EXISTS idx_issue ON lottery_results(issue)')
            cursor.execute('CREATE INDEX IF NOT EXISTS idx_draw_date ON lottery_results(draw_date)')
            
            conn.commit()
            print("数据库表初始化成功")
            
        except sqlite3.Error as e:
            print(f"初始化数据库错误：{e}")
            raise
        finally:
            self.close()
    
    def insert_lottery_result(self, result_data):
        """
        插入单条开奖记录（如果期号已存在则更新）
        
        Args:
            result_data: 字典格式的开奖数据
        """
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            # 使用 INSERT OR REPLACE 实现：如果期号已存在则替换整条记录
            cursor.execute('''
                INSERT OR REPLACE INTO lottery_results 
                (issue, red_1, red_2, red_3, red_4, red_5, red_6, blue, draw_date, region)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                result_data['issue'],
                result_data['red_balls'][0],
                result_data['red_balls'][1],
                result_data['red_balls'][2],
                result_data['red_balls'][3],
                result_data['red_balls'][4],
                result_data['red_balls'][5],
                result_data['blue_ball'],
                result_data['draw_date'],
                result_data.get('region', '')  # 中奖地区，可选字段
            ))
            
            conn.commit()
            return cursor.lastrowid
            
        except sqlite3.Error as e:
            print(f"插入数据错误：{e}")
            raise
        finally:
            self.close()
    
    def get_latest_issue(self):
        """获取数据库中最新的期号"""
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            cursor.execute('SELECT issue FROM lottery_results ORDER BY draw_date DESC LIMIT 1')
            result = cursor.fetchone()
            
            return result['issue'] if result else None
            
        except sqlite3.Error as e:
            print(f"查询最新期号错误：{e}")
            return None
        finally:
            self.close()
    
    def get_all_results(self, order_by='draw_date'):
        """
        获取所有开奖记录
        
        Args:
            order_by: 排序字段，可选 'draw_date' 或 'issue'
            
        Returns:
            开奖记录列表
        """
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            order_clause = f"ORDER BY {order_by} ASC"
            cursor.execute(f'''
                SELECT * FROM lottery_results {order_clause}
            ''')
            
            results = cursor.fetchall()
            return [dict(row) for row in results]
            
        except sqlite3.Error as e:
            print(f"查询所有记录错误：{e}")
            return []
        finally:
            self.close()
    
    def get_recent_results(self, limit=10):
        """
        获取最近 N 期的开奖记录
        
        Args:
            limit: 获取数量
            
        Returns:
            开奖记录列表
        """
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            cursor.execute('''
                SELECT * FROM lottery_results 
                ORDER BY draw_date DESC 
                LIMIT ?
            ''', (limit,))
            
            results = cursor.fetchall()
            return [dict(row) for row in results]
            
        except sqlite3.Error as e:
            print(f"查询最近记录错误：{e}")
            return []
        finally:
            self.close()


# 单例模式
db_manager = DatabaseManager()

if __name__ == "__main__":
    # 测试数据库初始化
    db_manager.initialize()
    print("数据库测试完成")
