package com.naengsam.quick.domain.matching.service.engine;

/**
 * {@link MatchingEngine}의 소비 스레드에서 순차적으로 실행되는 작업. 액션이 필요한 대상(MatchingService)을 스스로 들고 있으므로 인자가 없다.
 */
@FunctionalInterface
public interface Action {

    void execute();
}
