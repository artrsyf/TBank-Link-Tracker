COMPOSE_DEV_FILE=./docker/docker-compose.yml

run:
	docker-compose -f $(COMPOSE_DEV_FILE) up

build:
	sbt clean compile docker:publishLocal
	sbt docker:stage
	docker-compose -f $(COMPOSE_DEV_FILE) up --build

down:
	docker-compose -f $(COMPOSE_DEV_FILE) down

drop:
	docker-compose -f $(COMPOSE_DEV_FILE) down --volumes --remove-orphans

.PHONY: run rebuild down drop