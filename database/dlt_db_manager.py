"""
大乐透数据库管理模块
负责 SQLite 数据库的连接、初始化和基本操作
"""
import sqlite3
from pathlib import Path
from datetime import datetime


class DLTDatabaseManager:
    """大乐透数据库管理类"""
    
    def __init__(self, db_path="data/dlt_lottery.db"):
        base_dir = Path(__file__).parent.parent
        self.db_path = base_dir / db_path
        self.conn = None
        
    def connect(self):
        try:
            self.conn = sqlite3.connect(str(self.db_path))
            self.conn.row_factory = sqlite3.Row
            return self.conn
        except sqlite3.Error as e:
            print(f"数据库连接错误：{e}")
            raise
    
    def close(self):
        if self.conn:
            self.conn.close()
            self.conn = None
    
    def initialize(self):
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS lottery_results (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    issue TEXT UNIQUE NOT NULL,
                    red_1 INTEGER NOT NULL,
                    red_2 INTEGER NOT NULL,
                    red_3 INTEGER NOT NULL,
                    red_4 INTEGER NOT NULL,
                    red_5 INTEGER NOT NULL,
                    blue_1 INTEGER NOT NULL,
                    blue_2 INTEGER NOT NULL,
                    draw_date DATE NOT NULL,
                    region TEXT
                )
            ''')
            
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS missing_stats (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    issue TEXT UNIQUE NOT NULL,
                    red_missing TEXT NOT NULL,
                    blue_missing TEXT NOT NULL,
                    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
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
                    num_34 INTEGER DEFAULT 0, num_35 INTEGER DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS blue_ball_missing (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    issue TEXT UNIQUE NOT NULL,
                    num_01 INTEGER DEFAULT 0, num_02 INTEGER DEFAULT 0, num_03 INTEGER DEFAULT 0,
                    num_04 INTEGER DEFAULT 0, num_05 INTEGER DEFAULT 0, num_06 INTEGER DEFAULT 0,
                    num_07 INTEGER DEFAULT 0, num_08 INTEGER DEFAULT 0, num_09 INTEGER DEFAULT 0,
                    num_10 INTEGER DEFAULT 0, num_11 INTEGER DEFAULT 0, num_12 INTEGER DEFAULT 0,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            cursor.execute('CREATE INDEX IF NOT EXISTS idx_issue ON lottery_results(issue)')
            cursor.execute('CREATE INDEX IF NOT EXISTS idx_draw_date ON lottery_results(draw_date)')
            
            conn.commit()
            print("大乐透数据库表初始化成功")
            
        except sqlite3.Error as e:
            print(f"初始化数据库错误：{e}")
            raise
        finally:
            self.close()
    
    def insert_lottery_result(self, result_data):
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            cursor.execute('''
                INSERT OR REPLACE INTO lottery_results 
                (issue, red_1, red_2, red_3, red_4, red_5, blue_1, blue_2, draw_date, region)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                result_data['issue'],
                result_data['red_balls'][0],
                result_data['red_balls'][1],
                result_data['red_balls'][2],
                result_data['red_balls'][3],
                result_data['red_balls'][4],
                result_data['blue_balls'][0],
                result_data['blue_balls'][1],
                result_data['draw_date'],
                result_data.get('region', '')
            ))
            
            conn.commit()
            return cursor.lastrowid
            
        except sqlite3.Error as e:
            print(f"插入数据错误：{e}")
            raise
        finally:
            self.close()
    
    def get_latest_issue(self):
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
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            order_clause = f"ORDER BY {order_by} ASC"
            cursor.execute(f'SELECT * FROM lottery_results {order_clause}')
            
            results = cursor.fetchall()
            return [dict(row) for row in results]
            
        except sqlite3.Error as e:
            print(f"查询所有记录错误：{e}")
            return []
        finally:
            self.close()
    
    def get_recent_results(self, limit=10):
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
    
    def insert_red_ball_missing(self, issue, missing_data):
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            fields = ['issue']
            values = [issue]
            placeholders = ['?']
            
            for num in range(1, 36):
                field_name = f'num_{num:02d}'
                fields.append(field_name)
                values.append(missing_data.get(num, 0))
                placeholders.append('?')
            
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
    
    def insert_blue_ball_missing(self, issue, missing_data):
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            fields = ['issue']
            values = [issue]
            placeholders = ['?']
            
            for num in range(1, 13):
                field_name = f'num_{num:02d}'
                fields.append(field_name)
                values.append(missing_data.get(num, 0))
                placeholders.append('?')
            
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
    
    def delete_all_red_ball_missing(self):
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
    
    def delete_all_blue_ball_missing(self):
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


dlt_db_manager = DLTDatabaseManager()


if __name__ == "__main__":
    dlt_db_manager.initialize()
    print("大乐透数据库测试完成")
