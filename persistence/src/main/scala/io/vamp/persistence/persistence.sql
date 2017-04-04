
CREATE DATABASE vamp;

CREATE TABLE `Artifacts` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Command` varchar(255) NOT NULL,
  `Type` varchar(255) NOT NULL,
  `Name` varchar(255) NOT NULL,
  `Definition` blob,
  PRIMARY KEY (`ID`)
) DEFAULT CHARSET=utf8;
