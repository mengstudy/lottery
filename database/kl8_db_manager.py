"""
福彩快乐8数据库管理模块
负责 SQLite 数据库的连接、初始化和基本操作
"""
import sqlite3
from pathlib import Path


class KL8DatabaseManager:
    """快乐8数据库管理类"""
    
    def __init__(self, db_path="data/kl8_lottery.db"):
        base_dir = Path(__file__).parent.parent
        self.db_path = base_dir / db_path
        self.conn = None
        
    def connect(self):
        try:
            self.conn = sqlite3.connect(str(self.db_path))
            self.conn.row_factory = sqlite3.Row
            return self.conn
        except sqlite3.Error as e:
            print(f"快乐8数据库连接错误：{e}")
            raise
    
    def close(self):
        if self.conn:
            self.conn.close()
            self.conn = None
    
    def initialize(self):
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            # 创建开奖结果表 (20个红球)
            red_cols_sql = ", ".join([f"red_{i} INTEGER NOT NULL" for i in range(1, 21)])
            cursor.execute(f'''
                CREATE TABLE IF NOT EXISTS lottery_results (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    issue TEXT UNIQUE NOT NULL,
                    {red_cols_sql},
                    draw_date DATE NOT NULL,
                    region TEXT
                )
            ''')
            
            # 创建红球遗漏统计表 (80个号码)
            missing_cols_sql = ", ".join([f"num_{i:02d} INTEGER DEFAULT 0" for i in range(1, 81)])
            cursor.execute(f'''
                CREATE TABLE IF NOT EXISTS red_ball_missing (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    issue TEXT UNIQUE NOT NULL,
                    {missing_cols_sql},
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            cursor.execute('CREATE INDEX IF NOT EXISTS idx_kl8_issue ON lottery_results(issue)')
            cursor.execute('CREATE INDEX IF NOT EXISTS idx_kl8_draw_date ON lottery_results(draw_date)')
            
            conn.commit()
            print("快乐8数据库表初始化成功")
            
        except sqlite3.Error as e:
            print(f"初始化快乐8数据库错误：{e}")
            raise
        finally:
            self.close()
    
    def insert_lottery_result(self, result_data):
        try:
            conn = self.connect()
            cursor = conn.cursor()
            
            # 动态生成字段名和占位符
            fields = ['issue']
            values = [result_data['issue']]
            placeholders = ['?']
            
            for i in range(1, 21):
                fields.append(f'red_{i}')
                values.append(result_data['red_balls'][i - 1])
                placeholders.append('?')
                
            fields.extend(['draw_date', 'region'])
            values.extend([result_data['draw_date'], result_data.get('region', '')])
            placeholders.extend(['?', '?'])
            
            sql = f'''
                INSERT OR REPLACE INTO lottery_results 
                ({', '.join(fields)})
                VALUES ({', '.join(placeholders)})
            '''
            
            cursor.execute(sql, values)
            conn.commit()
            return cursor.lastrowid
            
        except sqlite3.Error as e:
            print(f"插入快乐8开奖数据错误：{e}")
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
            print(f"查询快乐8最新期号错误：{e}")
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
            print(f"查询快乐8所有记录错误：{e}")
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
            print(f"查询快乐8最近记录错误：{e}")
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
            
            for num in range(1, 81):
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
            print(f"插入快乐8红球遗漏次数错误：{e}")
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
            print(f"删除快乐8红球遗漏数据错误：{e}")
            raise
        finally:
            self.close()


kl8_db_manager = KL8DatabaseManager()


if __name__ == "__main__":
    kl8_db_manager.initialize()
    print("快乐8数据库测试完成")
