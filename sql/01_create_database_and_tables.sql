/* Safe, idempotent setup. The production master/history tables are intentionally untouched. */
USE F2Database;
GO
IF OBJECT_ID(N'dbo.F2_FIXED_ASSET_IMPORT_HISTORY', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.F2_FIXED_ASSET_IMPORT_HISTORY (
        Id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_F2_FIXED_ASSET_IMPORT_HISTORY PRIMARY KEY,
        SourceType NVARCHAR(20) NULL, SourceName NVARCHAR(1000) NULL, SheetName NVARCHAR(255) NULL,
        [RowCount] INT NULL, [Status] NVARCHAR(20) NULL, ErrorMessage NVARCHAR(MAX) NULL,
        ImportedAt DATETIME2(0) NULL, ImportedBy NVARCHAR(255) NULL
    );
END;
GO
