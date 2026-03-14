"""
Flask 应用主入口
提供 Web 界面和 API 接口
"""
import json
from flask import Flask, render_template, jsonify, request
from pathlib import Path
import sys

# 添加项目路径
sys.path.insert(0, str(Path(__file__).parent))

from database.db_manager import db_manager
from analyzer.stats_calculator import calculator
from crawler.crawler import crawler
from functools import wraps


# 操作口令（统一配置）
OPERATION_PASSWORD = 'ssq2026'


def require_password(f):
    """装饰器：验证操作口令"""
    @wraps(f)
    def decorated_function(*args, **kwargs):
        data = request.get_json()
        password = data.get('password', '') if data else ''
        
        if password != OPERATION_PASSWORD:
            return jsonify({'success': False, 'message': '口令错误！'})
        
        return f(*args, **kwargs)
    return decorated_function


app = Flask(__name__)
app.config['JSON_AS_ASCII'] = False  # 支持中文


@app.route('/')
def index():
    """首页 - 最新数据看板"""
    try:
        # 获取最新一期数据
        latest_results = db_manager.get_recent_results(1)
        
        if not latest_results:
            return render_template('index.html', 
                                 error="暂无数据，请先更新数据")
        
        latest = latest_results[0]
        
        # 获取所有历史数据用于计算遗漏值
        all_results = db_manager.get_all_results(order_by='draw_date')
        
        # 转换数据格式
        formatted_results = []
        for result in all_results:
            formatted_results.append({
                'issue': result['issue'],
                'red_balls': [result['red_1'], result['red_2'], result['red_3'],
                             result['red_4'], result['red_5'], result['red_6']],
                'blue_ball': result.get('blue', 0),  # 使用 get 避免 KeyError
                'draw_date': str(result['draw_date'])
            })
        
        # 计算遗漏值
        missing_stats = calculator.calculate_missing_values(formatted_results)
        
        if missing_stats:
            latest_missing = missing_stats[-1]
            
            # 获取遗漏排行榜
            ranking = calculator.get_current_missing_ranking(latest_missing)
            
            # 分类冷热号
            hot_numbers, cold_numbers = calculator.classify_hot_cold_numbers(latest_missing)
            
            # 分析最新一期
            analysis = calculator.analyze_issue(latest, latest_missing)
            
            return render_template('index.html',
                                 latest=latest,
                                 analysis=analysis.to_dict(),
                                 red_ranking=ranking['red_ranking'],
                                 blue_ranking=ranking['blue_ranking'],
                                 hot_numbers=hot_numbers,
                                 cold_numbers=cold_numbers)
        else:
            return render_template('index.html', latest=latest, error="无法计算遗漏值")
            
    except Exception as e:
        return render_template('index.html', 
                             error=f"加载数据失败：{str(e)}",
                             latest=None)


@app.route('/history')
def history():
    """历史数据列表页面"""
    try:
        page = request.args.get('page', 1, type=int)
        per_page = 20
        
        # 获取所有数据（按 draw_date 升序排列）
        all_results = db_manager.get_all_results(order_by='draw_date')
        
        # 转换为字典列表
        results_list = []
        for result in all_results:
            results_list.append({
                'issue': result['issue'],
                'red_balls': [result['red_1'], result['red_2'], result['red_3'],
                             result['red_4'], result['red_5'], result['red_6']],
                'blue_ball': result.get('blue', 0),  # 使用 get 避免 KeyError
                'draw_date': str(result['draw_date']),
                'region': result.get('region', '')  # 中奖地区
            })
        
        # 计算遗漏值（需要升序排列）
        if results_list:
            # 复制一份用于计算遗漏值（保持升序）
            results_for_calc = results_list.copy()
            missing_stats = calculator.calculate_missing_values(results_for_calc)
                    
            # 倒序排列用于显示（最新的在前）
            results_list.reverse()
                
        # 分页
        total = len(results_list)
        start_idx = (page - 1) * per_page
        end_idx = start_idx + per_page
        paginated_results = results_list[start_idx:end_idx]
                
        # 为当前页的数据添加分析信息
        if results_list and missing_stats:
            # 找到当前页数据在倒序列表中的起始位置对应的正序索引
            for i, result in enumerate(paginated_results):
                # 在倒序列表中的索引
                reverse_idx = start_idx + i
                # 对应的正序索引（从后往前）
                forward_idx = len(results_list) -1 - reverse_idx
                        
                if 0 <= forward_idx < len(missing_stats):
                    analysis = calculator.analyze_issue(result, missing_stats[forward_idx])
                    result['odd_even'] = analysis.red_odd_even
                    result['max_red_missing'] = analysis.max_red_missing
                    result['blue_missing'] = analysis.blue_missing_value
                    # 添加红色中奖号码的遗漏次数列表
                    result['red_balls_missing'] = analysis.red_missing_values
        else:
            for result in paginated_results:
                result['odd_even'] = '-'
                result['max_red_missing'] = 0
                result['blue_missing'] = 0
        
        total_pages = (total + per_page - 1) // per_page
        
        # 计算显示的页码范围（最多显示 10 个页码）
        page_range = 10
        start_page = max(1, page - page_range // 2)
        end_page = min(total_pages, start_page + page_range - 1)
        
        # 调整起始页，确保始终显示 page_range 个页码
        if end_page - start_page + 1 < page_range:
            start_page = max(1, end_page - page_range + 1)
        
        return render_template('history.html',
                             results=paginated_results,
                             current_page=page,
                             total_pages=total_pages,
                             start_page=start_page,
                             end_page=end_page,
                             total=total)
                             
    except Exception as e:
        # 发生异常时也要传递必要的变量
        return render_template('history.html', 
                             error=f"加载历史数据失败：{str(e)}",
                             results=[],
                             current_page=page,
                             total_pages=0,
                             start_page=1,
                             end_page=0,
                             total=0)


@app.route('/missing_groups')
def missing_groups():
    """历史遗漏分组页面 - 显示每期的遗漏次数分组"""
    try:
        # 获取页码参数
        page = request.args.get('page', 1, type=int)
        per_page = 10  # 每页显示 10 期
        
        # 获取所有历史数据
        all_results = db_manager.get_all_results(order_by='draw_date')
        
        if not all_results:
            return render_template('missing_groups.html', 
                                 error="暂无数据，请先更新数据")
        
        # 转换数据格式
        formatted_results = []
        for result in all_results:
            formatted_results.append({
                'issue': result['issue'],
                'red_balls': [result['red_1'], result['red_2'], result['red_3'],
                             result['red_4'], result['red_5'], result['red_6']],
                'blue_ball': result.get('blue', 0),
                'draw_date': str(result['draw_date'])
            })
        
        # 计算遗漏值
        missing_stats = calculator.calculate_missing_values(formatted_results)
        
        # 为每期计算遗漏分组
        issues_with_groups = []
        for i, stat in enumerate(missing_stats):
            # 计算该期的遗漏分组
            missing_groups = calculator.calculate_all_red_missing_groups(stat)
            
            # 获取对应的开奖信息
            result = formatted_results[i] if i < len(formatted_results) else None
            
            if result:
                # 构建红球遗漏详情列表
                red_missing_details = []
                for j, red_ball in enumerate(result['red_balls']):
                    # 从 stat.red_missing 字典中获取该号码的遗漏值
                    missing_val = stat.red_missing.get(red_ball, 0)
                    red_missing_details.append({
                        'ball': red_ball,
                        'missing': missing_val
                    })
                
                issues_with_groups.append({
                    'issue': result['issue'],
                    'draw_date': result['draw_date'],
                    'red_balls': result['red_balls'],
                    'blue_ball': result['blue_ball'],
                    'missing_groups': {str(k): v for k, v in missing_groups.items()},
                    'red_missing_details': red_missing_details  # 添加红球遗漏详情
                })
        
        # 倒序排列（最新的在前）
        issues_with_groups.reverse()
        
        # 分页
        total = len(issues_with_groups)
        total_pages = (total + per_page - 1) // per_page
        
        # 确保页码在有效范围内
        if page < 1:
            page = 1
        elif page > total_pages:
            page = total_pages
        
        start_idx = (page - 1) * per_page
        end_idx = start_idx + per_page
        paginated_issues = issues_with_groups[start_idx:end_idx]
        
        # 计算分页导航
        start_page = max(1, page - 2)
        end_page = min(total_pages, page + 2)
        
        return render_template('missing_groups.html',
                             issues=paginated_issues,
                             current_page=page,
                             total_pages=total_pages,
                             start_page=start_page,
                             end_page=end_page,
                             total_count=total)
    except Exception as e:
        return render_template('missing_groups.html', 
                             error=f"加载数据失败：{str(e)}")


@app.route('/analysis/<issue>')
def analysis(issue):
    """单期详情分析页面"""
    try:
        # 获取该期数据
        all_results = db_manager.get_all_results(order_by='draw_date')
        
        target_result = None
        for result in all_results:
            if result['issue'] == issue:
                target_result = result
                break
        
        if not target_result:
            return render_template('analysis.html', error=f"未找到期号 {issue} 的数据")
        
        # 格式化数据
        formatted_result = {
            'issue': target_result['issue'],
            'red_balls': [target_result['red_1'], target_result['red_2'], 
                         target_result['red_3'], target_result['red_4'],
                         target_result['red_5'], target_result['red_6']],
            'blue_ball': target_result.get('blue', 0),  # 使用 get 避免 KeyError
            'draw_date': str(target_result['draw_date'])
        }
        
        # 计算遗漏值
        missing_stats = calculator.calculate_missing_values(all_results)
        
        # 找到对应的遗漏统计
        target_missing = None
        for stat in missing_stats:
            if stat.issue == issue:
                target_missing = stat
                break
        
        if target_missing:
            analysis_result = calculator.analyze_issue(formatted_result, target_missing)
            
            # 查找上一期数据
            prev_issue_data = None
            prev_analysis = None
            
            # 找到当前期在列表中的位置
            for i, result in enumerate(all_results):
                if result['issue'] == issue and i > 0:
                    # 获取上一期数据
                    prev_result = all_results[i - 1]
                    prev_issue_data = {
                        'issue': prev_result['issue'],
                        'red_balls': [prev_result['red_1'], prev_result['red_2'],
                                     prev_result['red_3'], prev_result['red_4'],
                                     prev_result['red_5'], prev_result['red_6']],
                        'blue_ball': prev_result.get('blue', 0),
                        'draw_date': str(prev_result['draw_date'])
                    }
                    
                    # 获取上一期的遗漏统计
                    for stat in missing_stats:
                        if stat.issue == prev_result['issue']:
                            # 计算上一期的遗漏分组
                            prev_missing_groups = calculator.calculate_all_red_missing_groups(stat)
                            # 将键转换为字符串，方便模板访问
                            prev_analysis = {
                                'missing_groups': {str(k): v for k, v in prev_missing_groups.items()}
                            }
                            break
                    break
            
            # 查找下一期数据
            next_issue_data = None
            next_analysis = None
            
            # 找到当前期在列表中的位置
            for i, result in enumerate(all_results):
                if result['issue'] == issue and i < len(all_results) - 1:
                    # 获取下一期数据（注意：all_results 是按开奖日期倒序排列的，所以下一期是 i+1）
                    next_result = all_results[i + 1]
                    next_issue_data = {
                        'issue': next_result['issue'],
                        'red_balls': [next_result['red_1'], next_result['red_2'],
                                     next_result['red_3'], next_result['red_4'],
                                     next_result['red_5'], next_result['red_6']],
                        'blue_ball': next_result.get('blue', 0),
                        'draw_date': str(next_result['draw_date'])
                    }
                    
                    # 获取下一期的遗漏统计
                    for stat in missing_stats:
                        if stat.issue == next_result['issue']:
                            # 计算下一期的遗漏分组
                            next_missing_groups = calculator.calculate_all_red_missing_groups(stat)
                            # 将键转换为字符串，方便模板访问
                            next_analysis = {
                                'missing_groups': {str(k): v for k, v in next_missing_groups.items()}
                            }
                            break
                    break
            
            # 如果没有下一期数据，模拟下期未开奖的情况（所有红球遗漏次数 +1，但本期开出号码遗漏为 0）
            if not next_analysis:
                # 构建模拟的遗漏分组
                # 规则：本期开出的 6 个红球遗漏值 = 0，其他红球遗漏值 = 当前遗漏值 + 1
                simulated_groups = {}
                
                # 获取本期开出的红球号码
                current_drawn_red_balls = set(analysis_result.red_balls)
                
                for red_num in range(1, 34):
                    if red_num in current_drawn_red_balls:
                        # 本期开出的号码，遗漏值为 0
                        missing_val = 0
                    else:
                        # 未开出的号码，遗漏值 +1
                        missing_val = target_missing.red_missing.get(red_num, 0) + 1
                    
                    # 将遗漏值≥9 的都归为"9"这一组
                    group_key = 9 if missing_val >= 9 else missing_val
                    
                    if group_key not in simulated_groups:
                        simulated_groups[group_key] = []
                    simulated_groups[group_key].append(red_num)
                
                next_analysis = {
                    'missing_groups': {str(k): v for k, v in simulated_groups.items()},
                    'no_next_draw': True  # 标记没有下一期开奖数据
                }
            
            # 构建号码遗漏详情
            number_details = []
            for i, red_ball in enumerate(formatted_result['red_balls']):
                number_details.append({
                    'type': '红球',
                    'number': red_ball,
                    'missing': analysis_result.red_missing_values[i]
                })
            
            number_details.append({
                'type': '蓝球',
                'number': formatted_result['blue_ball'],
                'missing': analysis_result.blue_missing_value
            })
            
            return render_template('analysis.html',
                                 issue_data=formatted_result,
                                 analysis=analysis_result.to_dict(),
                                 number_details=number_details,
                                 prev_issue_data=prev_issue_data,
                                 prev_analysis=prev_analysis,
                                 next_issue_data=next_issue_data,
                                 next_analysis=next_analysis)
        else:
            return render_template('analysis.html', 
                                 issue_data=formatted_result,
                                 error="无法计算遗漏值")
                                 
    except Exception as e:
        return render_template('analysis.html', 
                             error=f"加载分析数据失败：{str(e)}",
                             issue_data=None)


@app.route('/api/update_data', methods=['POST'])
@require_password
def update_data():
    """API: 手动触发数据更新"""
    try:
        # 抓取最新数据
        new_results = crawler.fetch_latest_data()
        
        if not new_results:
            return jsonify({'success': False, 'message': '抓取数据失败'})
        
        # 插入数据库
        inserted_count = 0
        for result in new_results:
            # 验证数据
            if crawler.validate_result(result):
                db_manager.insert_lottery_result(result)
                inserted_count += 1
        
        return jsonify({
            'success': True,
            'message': f'成功更新 {inserted_count} 条数据',
            'count': inserted_count
        })
        
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'更新失败：{str(e)}'
        })


@app.route('/api/update_all_history', methods=['POST'])
@require_password
def update_all_history():
    """API: 更新所有历史数据"""
    try:
        # 使用爬虫抓取所有历史数据
        all_results = crawler.fetch_all_history_by_api(max_pages=100)  # 最多抓取 100 页（3000 期）
        
        if not all_results:
            return jsonify({'success': False, 'message': '抓取历史数据失败'})
        
        # 插入数据库
        inserted_count = 0
        for result in all_results:
            # 验证数据
            if crawler.validate_result(result):
                db_manager.insert_lottery_result(result)
                inserted_count += 1
        
        return jsonify({
            'success': True,
            'message': f'成功更新 {inserted_count} 条历史数据',
            'count': inserted_count,
            'total': len(all_results)
        })
        
    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'更新失败：{str(e)}'
        })


@app.route('/api/missing_trend/<int:number>')
def missing_trend(number):
    """API: 获取指定号码的遗漏走势"""
    try:
        is_blue = request.args.get('is_blue', 'false').lower() == 'true'
        
        all_results = db_manager.get_all_results(order_by='draw_date')
        formatted_results = []
        for result in all_results:
            formatted_results.append({
                'issue': result['issue'],
                'red_balls': [result['red_1'], result['red_2'], result['red_3'],
                             result['red_4'], result['red_5'], result['red_6']],
                'blue_ball': result['blue'],
                'draw_date': str(result['draw_date'])
            })
        
        missing_stats = calculator.calculate_missing_values(formatted_results)
        
        # 获取遗漏趋势
        trend = calculator.get_missing_trend(number, missing_stats, is_blue)
        
        # 构建返回数据
        chart_data = {
            'labels': [stat.issue for stat in missing_stats],
            'values': trend
        }
        
        return jsonify(chart_data)
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/statistics')
def statistics():
    """API: 获取统计信息"""
    try:
        all_results = db_manager.get_all_results(order_by='draw_date')
        
        formatted_results = []
        for result in all_results:
            formatted_results.append({
                'issue': result['issue'],
                'red_balls': [result['red_1'], result['red_2'], result['red_3'],
                             result['red_4'], result['red_5'], result['red_6']],
                'blue_ball': result['blue'],
                'draw_date': str(result['draw_date'])
            })
        
        # 计算频率
        frequency = calculator.get_number_frequency(formatted_results)
        
        return jsonify(frequency)
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500


@app.route('/api/calculate_missing_tables', methods=['POST'])
@require_password
def calculate_missing_tables():
    """API: 一键计算遗漏次数表"""
    try:
        import time
        start_time = time.time()
        
        # 获取所有开奖数据（升序）
        all_results = db_manager.get_all_results(order_by='draw_date')
        
        if not all_results:
           return jsonify({'error': '没有开奖数据'}), 400
        
        total_count = len(all_results)
        processed_count = 0
        
        # 清空旧的遗漏次数表
        db_manager.delete_all_red_ball_missing()
        db_manager.delete_all_blue_ball_missing()
        
        # 遍历每一期，计算遗漏次数并保存
        for idx, result in enumerate(all_results):
            issue = result['issue']
            
            # 计算该期的遗漏次数
            red_missing, blue_missing = calculator.calculate_all_missing_for_issue(all_results, idx)
            
            # 保存到数据库
            db_manager.insert_red_ball_missing(issue, red_missing)
            db_manager.insert_blue_ball_missing(issue, blue_missing)
            
            processed_count += 1
            
            # 每处理 100 期打印一次进度
            if processed_count % 100 == 0:
               print(f"已处理 {processed_count}/{total_count} 期")
        
        elapsed_time = time.time() - start_time
        
        return jsonify({
            'success': True,
            'message': f'计算完成！共处理 {total_count} 期数据',
            'total': total_count,
            'processed': processed_count,
            'elapsed_seconds': round(elapsed_time, 2)
        })
        
    except Exception as e:
       print(f"计算遗漏次数错误：{e}")
       return jsonify({'error': str(e)}), 500


if __name__ == '__main__':
    # 初始化数据库
    db_manager.initialize()
    
    # 启动 Flask 应用
    app.run(debug=True, host='0.0.0.0', port=5000)
