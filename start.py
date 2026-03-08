#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
双色球数据分析网站 - 主启动脚本
"""
import sys
from pathlib import Path

# 添加项目根目录到 Python 路径
project_root = Path(__file__).parent
sys.path.insert(0, str(project_root))

from database.db_manager import db_manager


def main():
    """主函数"""
    print("=" * 60)
    print("🎱 双色球数据分析与遗漏值统计网站")
    print("=" * 60)
    
    # 初始化数据库
    print("\n正在初始化数据库...")
    db_manager.initialize()
    print("✓ 数据库初始化成功")
    
    # 启动 Flask 应用
    print("\n正在启动 Web 服务器...")
    print("访问地址：http://localhost:5000")
    print("按 Ctrl+C 停止服务\n")
    
    from app import app
    app.run(debug=True, host='0.0.0.0', port=5000)


if __name__ == '__main__':
    main()
