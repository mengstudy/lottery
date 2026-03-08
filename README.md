# 双色球数据分析与遗漏值统计网站

这是一个为彩票爱好者设计的双色球历史数据统计分析工具。基于 Python + Flask + SQLite + ECharts 构建。

## 功能特点

- ✅ **自动数据抓取**：支持中国福彩网和 500 彩票网数据源
- ✅ **遗漏值分析**：精确计算每个号码的遗漏期数
- ✅ **冷热号分类**：热号、温号、冷号、大冷号一目了然
- ✅ **奇偶比统计**：分析每期开奖号码的奇偶比例
- ✅ **可视化展示**：使用 ECharts 绘制遗漏走势图
- ✅ **定时更新**：APScheduler 自动在开奖时间后更新数据
- ✅ **响应式设计**：美观的渐变色界面，适配各种设备

## 技术栈

- **后端框架**: Flask 3.0.0
- **数据库**: SQLite
- **数据抓取**: requests + BeautifulSoup4
- **数据分析**: Pandas, collections
- **前端可视化**: ECharts 5.4.3
- **定时任务**: APScheduler 3.10.4

## 项目结构

```
ssq_analyzer/
├── app.py                      # Flask 应用主入口
├── start.py                    # 启动脚本
├── requirements.txt            # 依赖列表
├── database/
│   ├── db_manager.py           # 数据库管理模块
│   └── ssq_data.db             # SQLite 数据库文件（运行时生成）
├── crawler/
│   ├── crawler.py              # 爬虫核心逻辑
│   └── config.py               # 爬虫配置
├── analyzer/
│   ├── stats_calculator.py     # 统计计算模块
│   └── models.py               # 数据模型定义
├── templates/
│   ├── index.html              # 首页/最新数据看板
│   ├── history.html            # 历史数据列表
│   └── analysis.html           # 详细分析页面
├── static/
│   ├── css/                    # 样式文件
│   ├── js/                     # JavaScript 文件
│   └── images/                 # 图片资源
├── utils/
│   ├── helpers.py              # 工具函数
│   └── scheduler.py            # 定时任务调度器
└── crawler.log                 # 爬虫日志（运行时生成）
```

## 安装步骤

### 1. 环境要求

- Python 3.8+
- pip 包管理器

### 2. 安装依赖

```bash
cd ssq_analyzer
pip install -r requirements.txt
```

### 3. 运行项目

#### 方式一：使用启动脚本（推荐）

```bash
python start.py
```

#### 方式二：直接运行 Flask 应用

```bash
python app.py
```

### 4. 访问网站

打开浏览器访问：http://localhost:5000

## 使用说明

### 首页功能

- **最新一期展示**：显示最新开奖结果和基本信息
- **遗漏值排行榜**：红球和蓝球遗漏期数排名
- **冷热号分布**：直观展示当前热号和冷号
- **手动更新**：点击"立即更新"按钮获取最新数据

### 历史数据页面

- 分页展示所有历史开奖记录
- 显示每期的奇偶比、最大遗漏值等统计信息
- 点击期号可查看详细信息

### 详情分析页面

- 展示指定期次的完整开奖信息
- 分析开出号码的遗漏情况
- 显示热号和冷号列表

### 定时任务

系统默认配置：
- 执行时间：每周二、四、日 21:30（双色球开奖时间）
- 自动从官网抓取最新数据
- 增量更新，避免重复插入

如需修改定时任务配置，请编辑 `utils/scheduler.py` 文件。

## 数据说明

### 遗漏值定义

**遗漏值**：指某个号码自上次开出后，未出现的期数。

例如：某号码上次开出是 10 期前，之后连续 9 期都未开出，则当前遗漏值为 9。

### 冷热号分类标准

- **热号**：遗漏 0 次（最近一期刚开出）
- **温号**：遗漏 1-5 次
- **冷号**：遗漏 6-10 次
- **大冷号**：遗漏超过 10 次

### 数据来源

- 首选：中国福彩网 (https://www.cwl.gov.cn)
- 备选：500 彩票网 (http://datachart.500.com)

## API 接口

项目提供以下 RESTful API：

### 获取统计数据
```
GET /api/statistics
```

### 更新数据
```
POST /api/update_data
```

### 获取号码遗漏走势
```
GET /api/missing_trend/<number>?is_blue=true/false
```

## 常见问题

### Q: 数据抓取失败怎么办？
A: 系统会自动切换到备用数据源（500 彩票网）。如果仍然失败，可能是网络问题或网站反爬限制，可以手动点击"立即更新"重试。

### Q: 如何查看日志？
A: 爬虫日志保存在 `crawler.log` 文件中，可以使用文本编辑器查看。

### Q: 定时任务不执行怎么办？
A: 确保 `scheduler.py` 已启动。如果要独立运行定时任务，可以执行：
```bash
python utils/scheduler.py
```

### Q: 数据库在哪里？
A: SQLite 数据库文件位于 `database/ssq_data.db`，可以直接使用 SQLite 工具查看。

## 开发计划

- [ ] 增加更多图表类型（遗漏走势图、频率分布图）
- [ ] 添加用户系统和个性化功能
- [ ] 实现数据导出功能（Excel、CSV）
- [ ] 优化移动端体验
- [ ] 增加更多彩种支持（大乐透、福彩 3D 等）

## 注意事项

⚠️ **重要提示**：
- 本项目仅供数据分析和学习使用
- 彩票有风险，购买需谨慎
- 请勿沉迷彩票，理性投注

## 许可证

MIT License

## 作者

Developed with ❤️ by AI Assistant

## 更新日期

2026 年 3 月 7 日
