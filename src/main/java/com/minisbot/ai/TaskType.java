package com.minisbot.ai;

/**
 * 机器人可执行的任务类型
 */
public enum TaskType {
    /** 空闲 */
    IDLE,
    /** 跟随玩家 */
    FOLLOW_PLAYER,
    /** 挖矿 - 指定矿物 */
    MINE,
    /** 采集 - 指定方块 */
    COLLECT,
    /** 砍树 */
    CHOP_TREE,
    /** 种地/植树 */
    FARM,
    /** 战斗/反击 */
    FIGHT,
    /** 巡逻 */
    PATROL,
    /** 回家 */
    GO_HOME
}
