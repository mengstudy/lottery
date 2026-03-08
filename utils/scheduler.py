"""
定时任务模块
使用 APScheduler 实现定期数据更新
"""
from apscheduler.schedulers.blocking import BlockingScheduler
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
import logging
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).parent.parent))

from database.db_manager import db_manager
from crawler.crawler import crawler
from analyzer.stats_calculator import calculator


# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class DataUpdateScheduler:
    """数据更新调度器"""
    
    def __init__(self):
        # 使用后台调度器，不阻塞主线程
        self.scheduler = BackgroundScheduler()
        
    def update_lottery_data(self):
        """执行数据更新任务"""
        logger.info("开始执行定时数据更新任务...")
        
        try:
            # 抓取最新数据
            new_results = crawler.fetch_latest_data()
            
            if not new_results:
                logger.warning("未能抓取到新数据")
                return
            
            # 获取数据库最新期号
            latest_issue = db_manager.get_latest_issue()
            
            # 过滤已存在的数据
            to_insert = []
            for result in new_results:
                if latest_issue and result['issue'] <= latest_issue:
                    continue
                to_insert.append(result)
            
            if not to_insert:
                logger.info("数据库已是最新，无需更新")
                return
            
            # 插入新数据
            inserted_count = 0
            for result in to_insert:
                if crawler.validate_result(result):
                    db_manager.insert_lottery_result(result)
                    inserted_count += 1
            
            logger.info(f"成功更新 {inserted_count} 条数据")
            
            # 重新计算遗漏值（可选，用于缓存）
            # 这里可以添加缓存逻辑
            
        except Exception as e:
            logger.error(f"数据更新失败：{e}", exc_info=True)
    
    def start(self):
        """启动调度器"""
        # 配置定时任务：每周二、四、日 21:30 执行（双色球开奖时间）
        self.scheduler.add_job(
            self.update_lottery_data,
            CronTrigger(day_of_week='tue,thu,sun', hour=21, minute=30),
            id='update_lottery_data',
            name='双色球数据更新',
            replace_existing=True
        )
        
        # 也可以添加一个每天执行的版本作为备选
        # self.scheduler.add_job(
        #     self.update_lottery_data,
        #     CronTrigger(hour=21, minute=30),
        #     id='daily_update',
        #     name='每日数据更新',
        #     replace_existing=True
        # )
        
        self.scheduler.start()
        logger.info("定时任务已启动，将在每周二、四、日 21:30 自动更新数据")
    
    def stop(self):
        """停止调度器"""
        self.scheduler.shutdown()
        logger.info("定时任务已停止")


def create_scheduler():
    """创建并启动调度器"""
    scheduler = DataUpdateScheduler()
    return scheduler


if __name__ == "__main__":
    # 初始化数据库
    db_manager.initialize()
    
    # 创建调度器
    scheduler = create_scheduler()
    
    # 立即执行一次更新
    logger.info("启动时立即执行一次数据更新...")
    scheduler.update_lottery_data()
    
    # 启动定时任务
    try:
        scheduler.start()
    except (KeyboardInterrupt, SystemExit):
        scheduler.stop()
        logger.info("程序退出")
