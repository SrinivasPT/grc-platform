-- SQL Server initialization script
-- Creates the application database and app user with least-privilege access
-- Run once after first container start: docker exec grc_sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$SQLSERVER_SA_PASSWORD" -i /docker-entrypoint-initdb.d/01-init-db.sql -No

-- Create database
IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'grcplatform')
BEGIN
    CREATE DATABASE grcplatform
        COLLATE SQL_Latin1_General_CP1_CI_AS;
END
GO

USE grcplatform;
GO

-- Enable Change Tracking on database (required for Neo4j projection worker)
IF NOT EXISTS (
    SELECT 1 FROM sys.change_tracking_databases
    WHERE database_id = DB_ID('grcplatform')
)
BEGIN
    ALTER DATABASE grcplatform
    SET CHANGE_TRACKING = ON
    (CHANGE_RETENTION = 7 DAYS, AUTO_CLEANUP = ON);
END
GO

-- Create app user with least-privilege (no sa rights)
IF NOT EXISTS (SELECT name FROM sys.sql_logins WHERE name = N'grc_app')
BEGIN
    CREATE LOGIN grc_app WITH PASSWORD = '$(GRC_DB_PASSWORD)';
END
GO

IF NOT EXISTS (SELECT name FROM sys.database_principals WHERE name = N'grc_app')
BEGIN
    CREATE USER grc_app FOR LOGIN grc_app;
END
GO

-- Grant data operations only (no DDL rights — Liquibase runs as sa in dev)
ALTER ROLE db_datareader ADD MEMBER grc_app;
ALTER ROLE db_datawriter ADD MEMBER grc_app;
GRANT EXECUTE TO grc_app;
-- Required for Change Tracking queries (CHANGETABLE, CHANGE_TRACKING_CURRENT_VERSION)
GRANT VIEW DATABASE STATE TO grc_app;
GO
