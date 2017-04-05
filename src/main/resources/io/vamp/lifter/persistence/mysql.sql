
CREATE TABLE IF NOT EXISTS `Artifacts` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Version` varchar(255) NOT NULL,
  `Command` varchar(255) NOT NULL,
  `Type` varchar(255) NOT NULL,
  `Name` varchar(255) NOT NULL,
  `Definition` blob,
  PRIMARY KEY (`ID`)
) DEFAULT CHARSET=utf8;
