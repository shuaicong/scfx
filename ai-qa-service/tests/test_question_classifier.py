"""问题分类器单元测试"""
import pytest
from app.services.question_classifier import classify_question, QuestionType, _preprocess


class TestPreprocess:
    def test_fullwidth_to_halfwidth(self):
        assert _preprocess("２００") == "200"
        assert _preprocess("（价格）") == "(价格)"

    def test_zero_width_removed(self):
        assert _preprocess("价​格") == "价格"


class TestClassify:
    def test_price_question(self):
        assert classify_question("玉米今天多少钱") == QuestionType.PRICE

    def test_trend_question(self):
        assert classify_question("近期小麦走势如何") == QuestionType.TREND

    def test_policy_question(self):
        assert classify_question("国家储备政策是什么") == QuestionType.POLICY

    def test_general_fallback(self):
        assert classify_question("你好") == QuestionType.GENERAL

    def test_trend_over_price(self):
        assert classify_question("玉米价格走势") == QuestionType.TREND
