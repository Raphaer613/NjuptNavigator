package com.example.njuptcampusnavigator;

import com.baidu.mapapi.model.LatLng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CampusData {
    // Initial coordinates are centered on the Xianlin campus area and should be
    // replaced with the exact coordinates of each building before final delivery.
    public static final LatLng CAMPUS_CENTER = new LatLng(32.106261, 118.931664);

    private CampusData() {
    }

    public static List<Place> defaultPlaces() {
        return new ArrayList<>(Arrays.asList(
                new Place("图书馆", "学习场所", "08:00 - 22:00", "地上 6 层",
                        "综合图书馆，提供自习、借阅和数字资源服务。",
                        32.107600, 118.932700),
                new Place("教学楼一", "教学楼", "07:00 - 21:30", "地上 5 层",
                        "公共基础课和部分专业课程教学楼。",
                        32.105700, 118.930700),
                new Place("教学楼二", "教学楼", "07:00 - 21:30", "地上 6 层",
                        "多媒体教室、实验课和课程设计教学区域。",
                        32.106800, 118.934000),
                new Place("第一食堂", "餐饮服务", "06:30 - 21:00", "地上 3 层",
                        "提供早餐、午餐和晚餐，支持校园卡消费。",
                        32.103900, 118.929600),
                new Place("体育馆", "运动场所", "06:30 - 22:00", "地上 2 层",
                        "室内篮球、羽毛球和综合体育活动场地。",
                        32.102900, 118.934300),
                new Place("操场", "运动场所", "06:00 - 22:30", "室外场地",
                        "标准田径场和草坪，适合跑步、球类和集体活动。",
                        32.101500, 118.932100),
                new Place("学生宿舍区", "生活服务", "全天开放", "多栋 6 层",
                        "学生住宿区域，附近设有快递点和生活服务店。",
                        32.108200, 118.935000),
                new Place("校门", "校园出入口", "全天开放", "地面",
                        "校园主入口，连接校外公交站和共享单车点。",
                        32.100700, 118.926800)
        ));
    }
}
