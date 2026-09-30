USE F2Database;
GO
SELECT COUNT_BIG(*) AS CurrentAssets FROM dbo.F2_FIXED_ASSET;
SELECT TOP (20) Id, SourceType, SourceName, SheetName, [RowCount], [Status], ErrorMessage, ImportedAt, ImportedBy
FROM dbo.F2_FIXED_ASSET_IMPORT_HISTORY
ORDER BY ImportedAt DESC, Id DESC;
GO
