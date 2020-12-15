delimiter $$
create table `test` (`partition_id` int, `id` int, data1 int, data2 int, longdata varbinary(25000), PRIMARY KEY (`partition_id`,`id`), KEY `dindex` (`data1`)) engine ndb partition by key (partition_id);$$
delimiter $$
create table `results` (`id` int AUTO_INCREMENT, `test` varchar(25), `threads` int, `speed` double, `latency` double, `run` int, `batchSize` int,  `updateData` int, PRIMARY KEY (`id`))engine ndb $$
delimiter $$
