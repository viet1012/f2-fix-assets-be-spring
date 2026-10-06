/*
  Relocation requests: old RequestNo RL-YYYY-NNNN -> R + NNNN (e.g. RL-2026-0042 -> R0042).
  Run manually in SSMS (the application never runs this). Idempotent: only rows still named RL-… are touched.
  Stops without changing anything when two old codes would get the same new code (same NNNN in different years)
  or a new code already exists; resolve those by hand first.
  Files already written (drawings/Excel named with RL-…) and the Drawings column are NOT renamed; regenerate the
  Excel with POST /api/relocation-requests/{requestNo}/excel if needed.
*/
SET XACT_ABORT ON;
BEGIN TRAN;

IF OBJECT_ID('tempdb..#map') IS NOT NULL DROP TABLE #map;

SELECT DISTINCT RequestNo AS OldNo, 'R' + SUBSTRING(RequestNo, 9, 20) AS NewNo
INTO #map
FROM dbo.F2_FIXED_ASSET_HISTORY WITH (UPDLOCK, HOLDLOCK)
WHERE RequestNo LIKE 'RL-[0-9][0-9][0-9][0-9]-[0-9]%' AND Status LIKE 'REQ[_]%'
  AND TRY_CAST(SUBSTRING(RequestNo, 9, 20) AS int) IS NOT NULL;

/* Preview */
SELECT OldNo, NewNo FROM #map ORDER BY NewNo, OldNo;

IF EXISTS (SELECT NewNo FROM #map GROUP BY NewNo HAVING COUNT(*) > 1)
    THROW 50002, 'Several RL- codes map to the same R code (same number in different years); see the preview.', 1;

IF EXISTS (SELECT 1 FROM #map m JOIN dbo.F2_FIXED_ASSET_HISTORY h ON h.RequestNo = m.NewNo)
    THROW 50003, 'A target R code already exists in F2_FIXED_ASSET_HISTORY; see the preview.', 1;

UPDATE h SET RequestNo = m.NewNo
FROM dbo.F2_FIXED_ASSET_HISTORY h
JOIN #map m ON m.OldNo = h.RequestNo
WHERE h.Status LIKE 'REQ[_]%';

COMMIT;

SELECT RequestNo, COUNT(*) AS Machines FROM dbo.F2_FIXED_ASSET_HISTORY
WHERE Status LIKE 'REQ[_]%' GROUP BY RequestNo ORDER BY RequestNo;
