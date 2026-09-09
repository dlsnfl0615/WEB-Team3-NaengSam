## AWS EC2 t2.nano vs t3.micro 스펙 비교

| 항목 | t2.nano | t3.micro |
|---|---|---|
| vCPU | 1개 | 2개 |
| 메모리 | 0.5 GiB | 1.0 GiB |
| 세대 / 하이퍼바이저 | 2세대(Xen 기반) | 3세대(AWS Nitro System 기반) |
| CPU 베이스라인 성능 | vCPU당 5% | vCPU당 10% |
| CPU 크레딧 적립 | 시간당 3크레딧 | 시간당 12크레딧 |
| 네트워크 성능 | Low to Moderate | 최대 5Gbps (버스트) |
| EBS 최적화 | 미지원 | 지원 |
| 온디맨드 요금(서울 리전 기준, 대략) | 약 $0.0074/h | 약 $0.0104/h |
| 프리티어 대상 | 과거 프리티어 대상이었으나 현재는 t3.micro가 주력 | 현재 AWS 프리티어의 표준 인스턴스 |

두 인스턴스 모두 "버스터블 성능(Burstable Performance)" 인스턴스라는 공통점이 있습니다. 평상시에는 베이스라인 CPU 성능만 사용하고 남는 성능만큼 CPU 크레딧을 적립해 두었다가, 트래픽이 몰릴 때 크레딧을 소모하며 100%까지 순간적으로 버스트할 수 있는 구조입니다. t3 계열은 여기에 더해 "Unlimited 모드"를 지원해서, 크레딧이 소진돼도 추가 요금을 내고 베이스라인 이상 성능을 계속 쓸 수 있다는 차이가 있습니다(t2는 Standard 모드에서는 크레딧 소진 시 베이스라인으로 강제 하향).

### 각각 어디까지 감당 가능한가

**t2.nano (1 vCPU, 0.5GB)**
메모리가 0.5GB뿐이라 사실상 애플리케이션 서버로 쓰기엔 빠듯합니다. Spring Boot처럼 JVM 기반 프레임워크는 기본 힙 설정만으로도 수백MB를 잡아먹기 때문에, t2.nano에 Spring Boot 앱을 올리면 OOM(Out of Memory)이나 스왑 발생 가능성이 높습니다. 실무에서는 주로 NAT 인스턴스, VPN/베스천 호스트, 매우 가벼운 크론잡, 정적 파일을 서빙하는 nginx 리버스 프록시, 트래픽이 거의 없는 개인 블로그 정도가 현실적인 사용처입니다.

**t3.micro (2 vCPU, 1GB)**
메모리가 1GB로 두 배이고 vCPU도 2개라, JVM 힙을 축소 설정(`-Xmx512m` 등)하면 Spring Boot 애플리케이션을 올리는 것이 가능한 최소 스펙입니다. 다만 동시 접속자가 많아지거나 커넥션 풀(HikariCP), Tomcat 스레드 풀이 커지면 금방 메모리 압박이 옵니다. 개인 프로젝트/포트폴리오용 백엔드 서버, 소규모 트래픽의 REST API, 개발/스테이징 환경, 가벼운 RDS(MySQL/PostgreSQL) 인스턴스 정도가 현실적인 상한선이고, 프로덕션에서 동시 사용자 수십~백 단위를 넘어가면 t3.small(2GB) 이상으로 올리는 것이 일반적입니다.

Sources:
- [Amazon EC2 T3 Instances](https://aws.amazon.com/ec2/instance-types/t3/)
- [t2.nano pricing and specs](https://instances.vantage.sh/aws/ec2/t2.nano)
- [t3.micro pricing and specs](https://instances.vantage.sh/aws/ec2/t3.micro)