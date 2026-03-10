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
                    issue TEXT UNIQUE NOT NULL,
                    red_missing TEXT NOT NULL,  -- JSON 格式存储红球遗漏值
                    blue_missing TEXT NOT NULL,  -- JSON 格式存储蓝球遗漏值
                    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            # 创建红球遗漏次数表（详细记录每个红球号码的遗漏次数）
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS red_ball_missing (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    issue TEXT UNIQUE NOT NULL,
                    num_01 INTEGER DEFAULT 0, num_02 INTEGER DEFAULT 0, num_03 INTEGER DEFAULT 0,
                    num_04 INTEGER DEFAULT 0, num_05 INTEGER DEFAULT 0, num_06 INTEGER DEFAULT 0,
                    num_07 INTEGER DEFAULT 0, num_08 INTEGER DEFAULT 0, num_09 INTEGER DEFAULT 0,
                    num_10 INTEGER DEFAULT 0, num_11 INTEGER DEFAULT 0, num_12 INTEGER DEFAULT 0,
                    num_13 INTEGER DEFAULT 0, num_14 INTEGER DEFAULT 0, num_15 INTEGER DEFAULT 0,
                    num_16 INTEGER DEFAULT 0, num_17 INTEGER DEFAULT 0, num_18 INTEGER DEFAULT 0,
                    num_19 INTEGER DEFAULT 0, num_20 INTEGER DEFAULT 0, num_21 INTEGER DEFAULT 0,
                    num_22 INTEGER DEFAULT 0, num_23 INTEGER DEFAULT 0, num_24 INTEGER DEFAULT 0,
                    num_25 INTEGER DEFAULT 0, num_26 INTEGER DEFAULT 0, num_27 INTEGER DEFAULT 0,
                    num_28 INTEGER DEFAULT 0, num_29 INTEGER DEFAULT 0, num_30 INTEGER DEFAULT 0,
                    num_31 INTEGER DEFAULT 0, num_32 INTEGER DEFAULT 0, num_33 INTEGER DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            # 创建蓝球遗漏次数表（详细记录每个蓝球号码的遗漏次数）
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS blue_ball_missing (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    issue TEXT UNIQUE NOT NULL,
                    num_01 INTEGER DEFAULT 0, num_02 INTEGER DEFAULT 0, num_03 INTEGER DEFAULT 0,
                    num_04 INTEGER DEFAULT 0, num_05 INTEGER DEFAULT 0, num_06 INTEGER DEFAULT 0,
                    num_07 INTEGER DEFAULT 0, num_08 INTEGER DEFAULT 0, num_09 INTEGER DEFAULT 0,
                    num_10 INTEGER DEFAULT 0, num_11 INTEGER DEFAULT 0, num_12 INTEGER DEFAULT 0,
                    num_13 INTEGER DEFAULT 0, num_14 INTEGER DEFAULT 0, num_15 INTEGER DEFAULT 0,
                    num_16 INTEGER DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            # 创建索引以提高查询性能
            cursor.execute('CREATE INDEX IF NOT EXISTS idx_issue ON lottery_results(issue)')
            cursor.execute('CREATE INDEX IF NOT EXISTS idx_draw_date ON lottery_results(draw_date)')
            cursor.execute('CREATE INDEX IF NOT EXISTS idx_red_missing_issue ON red_ball_missing(issue)')
            cursor.execute('CREATE INDEX IF NOT EXISTS idx_blue_missing_issue ON blue_ball_missing(issue)')
            
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
    
    # ========== 红球遗漏次数表操作 ==========
    
    def insert_red_ball_missing(self, issue, missing_data):
        """
        插入或更新红球遗漏次数
        
        Args:
            issue: 期号
            missing_data: 字典，键为号码（01-33），值为遗漏次数
        """
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            # 构建字段和值
            fields = ['issue']
            values = [issue]
            placeholders = ['?']
            
            for num in range(1, 34):
                field_name = f'num_{num:02d}'
                fields.append(field_name)
                values.append(missing_data.get(num, 0))
                placeholders.append('?')
            
            # 使用 INSERT OR REPLACE
            sql = f'''
                INSERT OR REPLACE INTO red_ball_missing 
                ({', '.join(fields)}) 
                VALUES ({', '.join(placeholders)})
            '''
            
            cursor.execute(sql, values)
            conn.commit()
            
        except sqlite3.Error as e:
            print(f"插入红球遗漏次数错误：{e}")
            raise
        finally:
            self.close()
    
    def get_red_ball_missing(self, issue):
        """
        获取某期的红球遗漏次数
        
        Args:
            issue: 期号
            
        Returns:
            字典格式的遗漏次数数据
        """
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            cursor.execute('''
                SELECT * FROM red_ball_missing WHERE issue = ?
            ''', (issue,))
            
            result = cursor.fetchone()
            if result:
                return dict(result)
            return None
            
        except sqlite3.Error as e:
            print(f"查询红球遗漏次数错误：{e}")
            return None
        finally:
            self.close()
    
    def delete_all_red_ball_missing(self):
        """删除所有红球遗漏次数数据"""
        try:
            conn = self.connect()
            cursor = conn.cursor()
            cursor.execute('DELETE FROM red_ball_missing')
            conn.commit()
            
        except sqlite3.Error as e:
            print(f"删除红球遗漏次数错误：{e}")
            raise
        finally:
            self.close()
    
    # ========== 蓝球遗漏次数表操作 ==========
    
    def insert_blue_ball_missing(self, issue, missing_data):
        """
        插入或更新蓝球遗漏次数
        
        Args:
            issue: 期号
            missing_data: 字典，键为号码（01-16），值为遗漏次数
        """
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            # 构建字段和值
            fields = ['issue']
            values = [issue]
            placeholders = ['?']
            
            for num in range(1, 17):
                field_name = f'num_{num:02d}'
                fields.append(field_name)
                values.append(missing_data.get(num, 0))
                placeholders.append('?')
            
            # 使用 INSERT OR REPLACE
            sql = f'''
                INSERT OR REPLACE INTO blue_ball_missing 
                ({', '.join(fields)}) 
                VALUES ({', '.join(placeholders)})
            '''
            
            cursor.execute(sql, values)
            conn.commit()
            
        except sqlite3.Error as e:
            print(f"插入蓝球遗漏次数错误：{e}")
            raise
        finally:
            self.close()
    
    def get_blue_ball_missing(self, issue):
        """
        获取某期的蓝球遗漏次数
        
        Args:
            issue: 期号
            
        Returns:
            字典格式的遗漏次数数据
        """
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            cursor.execute('''
                SELECT * FROM blue_ball_missing WHERE issue = ?
            ''', (issue,))
            
            result = cursor.fetchone()
            if result:
                return dict(result)
            return None
            
        except sqlite3.Error as e:
            print(f"查询蓝球遗漏次数错误：{e}")
            return None
        finally:
            self.close()
    
    def delete_all_blue_ball_missing(self):
        """删除所有蓝球遗漏次数数据"""
        try:
            conn = self.connect()
            cursor = conn.cursor()
            cursor.execute('DELETE FROM blue_ball_missing')
            conn.commit()
            
        except sqlite3.Error as e:
            print(f"删除蓝球遗漏次数错误：{e}")
            raise
        finally:
            self.close()


# 单例模式
db_manager = DatabaseManager()

if __name__ == "__main__":
    # 测试数据库初始化
    db_manager.initialize()
    print("数据库测试完成")
