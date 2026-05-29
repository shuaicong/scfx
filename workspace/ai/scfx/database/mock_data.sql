-- =============================================
-- 采集脚本 Mock 数据
-- =============================================

-- 清空现有数据（如果需要重新初始化）
-- DELETE FROM t_script_version WHERE script_id > 0;
-- DELETE FROM t_collection_script WHERE id > 0;

-- 插入采集脚本数据
INSERT INTO t_collection_script (script_name, description, script_path, source, status, trigger_type, cron_expression, execution_count, success_count, failed_count, last_execution_time, next_execution_time, created_by, created_at)
VALUES
(
    '粮信网玉米晨报采集',
    '每日定时采集粮信网玉米价格数据，生成晨报报告',
    '/scripts/liangxin_corn_morning.py',
    'liangxin',
    'enabled',
    'cron',
    '0 8 * * *',
    156,
    153,
    3,
    '2026-05-12 08:00:00',
    '2026-05-13 08:00:00',
    'admin',
    '2026-04-15 10:00:00'
);

INSERT INTO t_collection_script (script_name, description, script_path, source, status, trigger_type, repeat_type, repeat_time, execution_count, success_count, failed_count, last_execution_time, next_execution_time, created_by, created_at)
VALUES
(
    '我的钢铁网螺纹钢采集',
    '定时采集钢铁价格走势，每日14:30执行',
    '/scripts/mysteel_rebar.py',
    'mysteel',
    'enabled',
    'repeat',
    'daily',
    '14:30',
    89,
    77,
    12,
    '2026-05-12 14:30:00',
    '2026-05-13 14:30:00',
    'admin',
    '2026-04-18 09:00:00'
);

INSERT INTO t_collection_script (script_name, description, script_path, source, status, trigger_type, cron_expression, execution_count, success_count, failed_count, last_execution_time, next_execution_time, created_by, created_at)
VALUES
(
    'USDA出口报告采集',
    '采集美国农业部每周出口数据报告',
    '/scripts/usda_export.py',
    'usda',
    'enabled',
    'cron',
    '0 10 * * 5',
    24,
    22,
    2,
    '2026-05-09 10:00:00',
    '2026-05-16 10:00:00',
    'admin',
    '2026-04-20 14:00:00'
);

INSERT INTO t_collection_script (script_name, description, script_path, source, status, trigger_type, repeat_type, repeat_time, execution_count, success_count, failed_count, last_execution_time, created_by, created_at)
VALUES
(
    '中华粮网小麦周报',
    '每周一定期采集小麦价格数据',
    '/scripts/chinagrain_wheat.py',
    'chinagrain',
    'disabled',
    'repeat',
    'weekly',
    '09:15',
    47,
    45,
    2,
    '2026-04-20 09:15:00',
    'admin',
    '2026-04-22 11:00:00'
);

INSERT INTO t_collection_script (script_name, description, script_path, source, status, trigger_type, execution_count, success_count, failed_count, last_execution_time, next_execution_time, created_by, created_at)
VALUES
(
    '大豆现货价格采集',
    '采集各港口大豆现货价格数据，每日16:00执行',
    '/scripts/soybean_spot.py',
    'market',
    'enabled',
    'cron',
    '0 16 * * *',
    234,
    226,
    8,
    '2026-05-12 16:00:00',
    '2026-05-13 16:00:00',
    'admin',
    '2026-04-10 08:00:00'
);

INSERT INTO t_collection_script (script_name, description, script_path, source, status, trigger_type, execution_count, success_count, failed_count, last_execution_time, next_execution_time, created_by, created_at)
VALUES
(
    '玉米期货行情采集',
    '采集大连商品交易所玉米期货行情数据',
    '/scripts/corn_futures.py',
    'liangxin',
    'enabled',
    'cron',
    '0 9,15 * * *',
    312,
    305,
    7,
    '2026-05-12 15:00:00',
    '2026-05-13 09:00:00',
    'admin',
    '2026-03-28 10:00:00'
);

INSERT INTO t_collection_script (script_name, description, script_path, source, status, trigger_type, cron_expression, execution_count, success_count, failed_count, last_execution_time, created_by, created_at)
VALUES
(
    '粳米价格监测',
    '采集全国主要批发市场粳米价格',
    '/scripts/japonica_rice.py',
    'chinagrain',
    'enabled',
    'cron',
    '0 11 * * *',
    180,
    175,
    5,
    '2026-05-11 11:00:00',
    'admin',
    '2026-04-05 15:00:00'
);

INSERT INTO t_collection_script (script_name, description, script_path, source, status, trigger_type, execution_count, success_count, failed_count, created_by, created_at)
VALUES
(
    '一次性测试采集',
    '单次触发测试脚本，用于验证采集流程',
    '/scripts/test_once.py',
    'market',
    'disabled',
    'once',
    5,
    5,
    0,
    'admin',
    '2026-05-10 16:00:00'
);

-- =============================================
-- 脚本版本历史数据
-- =============================================

-- 为粮信网玉米晨报采集添加版本历史
INSERT INTO t_script_version (script_id, version_num, script_content, trigger_type, cron_expression, change_description, created_by, created_at, is_current)
VALUES
(1, 1, '# -*- coding: utf-8 -*-\nimport requests\nfrom bs4 import BeautifulSoup\n\ndef fetch_corn_price():\n    """采集粮信网玉米价格数据"""\n    url = "https://www.chinagrain.cn/report/"', 'cron', '0 8 * * *', '初始版本', 'admin', '2026-04-15 10:00:00', FALSE);

INSERT INTO t_script_version (script_id, version_num, script_content, trigger_type, cron_expression, change_description, created_by, created_at, is_current)
VALUES
(1, 2, '# -*- coding: utf-8 -*-\nimport requests\nfrom bs4 import BeautifulSoup\n\ndef fetch_corn_price():\n    """采集粮信网玉米价格数据 - v2"""\n    url = "https://www.chinagrain.cn/report/corn/"', 'cron', '0 8 * * *', '优化页面解析逻辑，添加重试机制', 'admin', '2026-04-28 14:30:00', FALSE);

INSERT INTO t_script_version (script_id, version_num, script_content, trigger_type, cron_expression, change_description, created_by, created_at, is_current)
VALUES
(1, 3, '# -*- coding: utf-8 -*-\nimport requests\nfrom bs4 import BeautifulSoup\nimport logging\n\nlogging.basicConfig(level=logging.INFO)\nlogger = logging.getLogger(__name__)\n\ndef fetch_corn_price():\n    """采集粮信网玉米价格数据 - v3"""\n    logger.info("开始采集玉米数据")\n    url = "https://www.chinagrain.cn/report/corn/"', 'cron', '0 8 * * *', '添加日志和异常处理，完善错误重试机制', 'admin', '2026-05-05 09:00:00', TRUE);

-- 为我的钢铁网螺纹钢采集添加版本历史
INSERT INTO t_script_version (script_id, version_num, script_content, trigger_type, repeat_type, repeat_time, change_description, created_by, created_at, is_current)
VALUES
(2, 1, '# -*- coding: utf-8 -*-\nimport requests\n\ndef fetch_steel_price():\n    """采集螺纹钢价格"""\n    pass', 'repeat', 'daily', '14:30', '初始版本', 'admin', '2026-04-18 09:00:00', TRUE);

-- =============================================
-- 执行记录数据
-- =============================================

INSERT INTO t_task_execution (script_id, execution_id, trigger_type, status, start_time, end_time, duration_ms, error_message, created_at)
VALUES
(1, 'exec_20260512080001', 'scheduled', 'success', '2026-05-12 08:00:00', '2026-05-12 08:00:45', 45000, NULL, '2026-05-12 08:00:00');

INSERT INTO t_task_execution (script_id, execution_id, trigger_type, status, start_time, end_time, duration_ms, error_message, created_at)
VALUES
(1, 'exec_20260511080002', 'scheduled', 'success', '2026-05-11 08:00:00', '2026-05-11 08:00:38', 38000, NULL, '2026-05-11 08:00:00');

INSERT INTO t_task_execution (script_id, execution_id, trigger_type, status, start_time, end_time, duration_ms, error_message, created_at)
VALUES
(1, 'exec_20260510080001', 'scheduled', 'failed', '2026-05-10 08:00:00', '2026-05-10 08:00:12', 12000, '网络超时，无法连接到数据源', '2026-05-10 08:00:00');

INSERT INTO t_task_execution (script_id, execution_id, trigger_type, status, start_time, end_time, duration_ms, error_message, created_at)
VALUES
(2, 'exec_20260512143001', 'scheduled', 'success', '2026-05-12 14:30:00', '2026-05-12 14:30:52', 52000, NULL, '2026-05-12 14:30:00');

INSERT INTO t_task_execution (script_id, execution_id, trigger_type, status, start_time, end_time, duration_ms, error_message, created_at)
VALUES
(5, 'exec_20260512160001', 'scheduled', 'success', '2026-05-12 16:00:00', '2026-05-12 16:01:15', 75000, NULL, '2026-05-12 16:00:00');

INSERT INTO t_task_execution (script_id, execution_id, trigger_type, status, start_time, end_time, duration_ms, error_message, created_at)
VALUES
(6, 'exec_20260512090001', 'scheduled', 'success', '2026-05-12 09:00:00', '2026-05-12 09:00:33', 33000, NULL, '2026-05-12 09:00:00');

INSERT INTO t_task_execution (script_id, execution_id, trigger_type, status, start_time, end_time, duration_ms, error_message, created_at)
VALUES
(6, 'exec_20260512150001', 'scheduled', 'success', '2026-05-12 15:00:00', '2026-05-12 15:00:41', 41000, NULL, '2026-05-12 15:00:00');

-- 执行日志
INSERT INTO t_task_execution_log (execution_id, script_id, level, message, timestamp)
VALUES
('exec_20260512080001', 1, 'INFO', '任务开始执行', '2026-05-12 08:00:00');

INSERT INTO t_task_execution_log (execution_id, script_id, level, message, timestamp)
VALUES
('exec_20260512080001', 1, 'INFO', '正在连接数据源...', '2026-05-12 08:00:05');

INSERT INTO t_task_execution_log (execution_id, script_id, level, message, timestamp)
VALUES
('exec_20260512080001', 1, 'INFO', '成功获取页面内容，开始解析...', '2026-05-12 08:00:25');

INSERT INTO t_task_execution_log (execution_id, script_id, level, message, timestamp)
VALUES
('exec_20260512080001', 1, 'INFO', '采集完成，共获取15条数据', '2026-05-12 08:00:40');

INSERT INTO t_task_execution_log (execution_id, script_id, level, message, timestamp)
VALUES
('exec_20260512080001', 1, 'INFO', '任务执行成功，耗时45秒', '2026-05-12 08:00:45');