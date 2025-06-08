![Build](https://github.com/central-university-dev/backend-academy-2025-spring-scala-template/actions/workflows/ci.yml/badge.svg)

# **Link Tracker**

Проект сделан в рамках курса Академия Бэкенда.

Приложение для отслеживания обновлений контента по ссылкам.
При появлении новых событий отправляется уведомление в Telegram.

Проект написан на `Scala 3`.

Проект состоит из 2-х приложений:
* Bot
* Scrapper

## **Работа с приложением**

### Клонирование репозитория
```sh
git clone https://github.com/central-university-dev/scala-artrsyf.git
cd scala-artrsyf
git checkout http_mvp
```

### Подготовка секретов
Необходимо создать .env файл в директории scala-artrsyf/bot/src/resources/ и перенести в нее енвы из .env.example

### Запуск проекта
```sh
make run
```

### Билд проекта (тут также происходит подготовка Docker-образов)
```sh
make build
```

### Остановка проекта
```sh
make down
```

### Остановка проекта + удаление контейнеров, вольюмов
```sh
make drop
```
