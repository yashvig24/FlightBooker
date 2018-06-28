-- add all your SQL setup statements here. 

-- You can assume that the following base table has been created with data loaded for you when we test your submission 
-- (you still need to create and populate it in your instance however),
-- although you are free to insert extra ALTER COLUMN ... statements to change the column 
-- names / types if you like.

--FLIGHTS (fid int, 
--         month_id int,        -- 1-12
--         day_of_month int,    -- 1-31 
--         day_of_week_id int,  -- 1-7, 1 = Monday, 2 = Tuesday, etc
--         carrier_id varchar(7), 
--         flight_num int,
--         origin_city varchar(34), 
--         origin_state varchar(47), 
--         dest_city varchar(34), 
--         dest_state varchar(46), 
--         departure_delay int, -- in mins
--         taxi_out int,        -- in mins
--         arrival_delay int,   -- in mins
--         canceled int,        -- 1 means canceled
--         actual_time int,     -- in mins
--         distance int,        -- in miles
--         capacity int, 
--         price int            -- in $             
--         )

create table carriers(
    cid varchar(100) primary key, 
    name varchar(100)
);

create table months(
    mid int primary key, 
    month varchar(100)
);

create table weekdays(
    did int primary key, 
    days_of_week varchar(100)
);

create table flights(
    fid int primary key, 
    month_id int references months, 
    day_of_month int, 
    day_of_week_id int references weekdays, 
    carrier_id varchar(100) references carriers, 
    flight_num int, 
    origin_city varchar(100), 
    origin_state varchar(100), 
    dest_city varchar(100), 
    dest_state varchar(100), 
    departure_delay int, 
    taxi_out int, 
    arrival_delay int, 
    canceled int, 
    actual_time int, 
    distance int, 
    capacity int, 
    price int
);

create table users (
    username varchar(100) primary key,
    password varchar(100),
    balance int
);

create table reservations (
    res_id int primary key not null identity(1,1) ,
    fid1 int references flights,
    fid2 int references flights,
    username varchar(100) references users,
    paid int
);

create table seatChange(
    fid int references flights,
    seats_left int
);