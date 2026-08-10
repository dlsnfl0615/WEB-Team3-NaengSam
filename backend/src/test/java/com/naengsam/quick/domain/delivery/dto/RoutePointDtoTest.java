package com.naengsam.quick.domain.delivery.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.naengsam.quick.domain.address.dto.KakaoDirectionsResponseDto;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 카카오 Route → 이동경로 좌표 목록 평탄화 로직 단위 테스트.
 */
class RoutePointDtoTest {

    private static KakaoDirectionsResponseDto.Step step(double[][] points) {
        return new KakaoDirectionsResponseDto.Step(
                new KakaoDirectionsResponseDto.StepProperties(0, "안내", 0, 0, 0),
                new KakaoDirectionsResponseDto.Path(points));
    }

    private static KakaoDirectionsResponseDto.Leg leg(KakaoDirectionsResponseDto.Step... steps) {
        return new KakaoDirectionsResponseDto.Leg(
                new KakaoDirectionsResponseDto.LegProperties(0, 0), steps);
    }

    private static KakaoDirectionsResponseDto.Route route(KakaoDirectionsResponseDto.Leg... legs) {
        return new KakaoDirectionsResponseDto.Route(
                new KakaoDirectionsResponseDto.Properties(0, 0), legs);
    }

    @Test
    void points의_경도위도_순서를_위도경도로_뒤집어_평탄화한다() {
        KakaoDirectionsResponseDto.Route route = route(leg(
                step(new double[][]{{127.02700693, 37.49864277}, {127.02698289, 37.49863151}})));

        List<RoutePointDto> result = RoutePointDto.from(route);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().latitude()).isEqualTo(37.49864277);
        assertThat(result.getFirst().longitude()).isEqualTo(127.02700693);
    }

    @Test
    void 여러_leg와_step의_좌표를_순서대로_이어붙인다() {
        KakaoDirectionsResponseDto.Route route = route(
                leg(step(new double[][]{{127.0, 37.0}}), step(new double[][]{{127.1, 37.1}})),
                leg(step(new double[][]{{127.2, 37.2}})));

        List<RoutePointDto> result = RoutePointDto.from(route);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(RoutePointDto::longitude)
                .containsExactly(127.0, 127.1, 127.2);
    }

    @Test
    void legs가_null이면_빈_목록을_반환한다() {
        KakaoDirectionsResponseDto.Route route = new KakaoDirectionsResponseDto.Route(
                new KakaoDirectionsResponseDto.Properties(0, 0), null);

        assertThat(RoutePointDto.from(route)).isEmpty();
    }
}
