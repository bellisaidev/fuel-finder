create table shedlock (
    name        varchar(64)  not null,
    lock_until  timestamptz  not null,
    locked_at   timestamptz  not null,
    locked_by   varchar(255) not null,
    primary key (name)
);