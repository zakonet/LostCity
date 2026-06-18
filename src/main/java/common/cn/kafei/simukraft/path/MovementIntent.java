package common.cn.kafei.simukraft.path;

public enum MovementIntent {
    WALK,
    // WANDER：优先级最低的闲逛意图，路径失败时直接放弃，不触发传送。
    WANDER,
    RUN,
    WORK,
    // SELF_FEEDING：自动买饭流程独占导航意图，防止普通工作移动抢占。
    SELF_FEEDING,
    RETURN_HOME
}
