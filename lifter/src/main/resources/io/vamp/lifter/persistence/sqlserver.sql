
if not exists (select * from sysobjects where name='Artifacts' and xtype='U')
    create table Artifacts (
      ID BIGINT NOT NULL IDENTITY(1,1) PRIMARY KEY,
      Version VARCHAR(255) NOT NULL,
      Command VARCHAR(255) NOT NULL,
      Type VARCHAR(255) NOT NULL,
      Name VARCHAR(255) NOT NULL,
      Definition VARCHAR(MAX)
    );
