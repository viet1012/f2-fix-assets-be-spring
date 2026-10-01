/*
  Relocation requests: remove the PE approval step.
  New flow: REQ_PENDING -> REQ_APPROVED / REQ_REJECTED -> REQ_DONE.
  Run manually in SSMS (the application never runs this). Idempotent.
  ASSUMPTION: UX_F2FAH_OpenRequest is a unique index on (MachineCode); check the current definition first:
    SELECT i.name, c.name AS col, i.filter_definition FROM sys.indexes i
    JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
    JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
    WHERE i.object_id = OBJECT_ID(N'dbo.F2_FIXED_ASSET_HISTORY') AND i.name = N'UX_F2FAH_OpenRequest';
*/
SET XACT_ABORT ON;
BEGIN TRAN;

UPDATE dbo.F2_FIXED_ASSET_HISTORY
SET Status = 'REQ_PENDING'
WHERE Status IN ('REQ_PENDING_PE', 'REQ_PENDING_BOD');

/* A machine must not be open twice under the new filter; stop if it is. */
IF EXISTS (
    SELECT MachineCode FROM dbo.F2_FIXED_ASSET_HISTORY
    WHERE Status IN ('REQ_PENDING', 'REQ_APPROVED')
    GROUP BY MachineCode HAVING COUNT(*) > 1
)
    THROW 50001, 'Duplicate open relocation rows per MachineCode; resolve them before recreating UX_F2FAH_OpenRequest.', 1;

IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.F2_FIXED_ASSET_HISTORY') AND name = N'UX_F2FAH_OpenRequest')
    DROP INDEX UX_F2FAH_OpenRequest ON dbo.F2_FIXED_ASSET_HISTORY;

CREATE UNIQUE NONCLUSTERED INDEX UX_F2FAH_OpenRequest
    ON dbo.F2_FIXED_ASSET_HISTORY (MachineCode)
    WHERE Status IN ('REQ_PENDING', 'REQ_APPROVED');

COMMIT;

SELECT Status, COUNT(*) AS Cnt FROM dbo.F2_FIXED_ASSET_HISTORY WHERE Status LIKE 'REQ[_]%' GROUP BY Status;
