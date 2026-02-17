


create table accounts (
    id varchar(36) primary key,
    balance numeric not null
);
insert into accounts (id, balance) values ('11', 1000);
insert into accounts (id, balance) values ('22', 1000);