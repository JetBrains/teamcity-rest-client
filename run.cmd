cd %~dp0\teamcity-rest-client-impl\src\test\environment
docker-compose rm --stop --force
docker-compose build --pull
docker-compose up
