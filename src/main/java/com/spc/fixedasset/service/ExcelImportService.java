package com.spc.fixedasset.service;

import com.spc.fixedasset.exception.ImportException;
import com.spc.fixedasset.model.ImportedFixedAsset;
import com.spc.fixedasset.model.ParsedWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExcelImportService {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd[ HH:mm[:ss]]");

    public ParsedWorkbook parse(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = resolveSheet(workbook);
            DataFormatter f = new DataFormatter(); FormulaEvaluator e = workbook.getCreationHelper().createFormulaEvaluator();
            int hi = findHeaderRow(sheet, f, e);
            if (hi < 0) throw new ImportException("Header row containing FixedAssetName was not found in the first 20 rows.");
            Columns c = columns(sheet.getRow(hi), f, e);
            if (c.machineCode < 0) throw new ImportException("Required asset identifier column fI was not found.");
            List<ImportedFixedAsset> rows = new ArrayList<>(); Set<String> seen = new HashSet<>(); Set<String> present = c.present();
            for (int i=hi+1;i<=sheet.getLastRowNum();i++) {
                Row r=sheet.getRow(i); String code=text(r,c.machineCode,f,e);
                if(code==null||!seen.add(code)) continue;
                rows.add(new ImportedFixedAsset(code,text(r,c.faName,f,e),text(r,c.physicalCount,f,e),text(r,c.faType,f,e),
                    text(r,c.kindExpense,f,e),text(r,c.kindFixedAsset,f,e),date(r,c.dateStart,f,e),decimal(r,c.histotyCost,f,e),
                    decimal(r,c.netBookValue,f,e),decimal(r,c.yearDepreciation,f,e),text(r,c.group,f,e),text(r,c.emailChecked,f,e),
                    text(r,c.emailApproved,f,e),text(r,c.div,f,e),text(r,c.factory,f,e),text(r,c.floor,f,e),text(r,c.positionA,f,e),
                    text(r,c.positionAA,f,e),text(r,c.positionAAA,f,e),text(r,c.invoiceNo,f,e),text(r,c.maker,f,e),
                    text(r,c.purchasedFrom,f,e),text(r,c.type,f,e),text(r,c.serialNo,f,e),text(r,c.checkMETI,f,e),
                    text(r,c.photo1,f,e),text(r,c.photo2,f,e),text(r,c.photo3,f,e),text(r,c.photoJudge,f,e),
                    text(r,c.machineStatus,f,e),present));
            }
            if(rows.isEmpty()) throw new ImportException("No asset rows were found after the header row.");
            return new ParsedWorkbook(sheet.getSheetName(),rows);
        } catch(ImportException x){throw x;} catch(Exception x){throw new ImportException("Unable to read Excel file: "+rootMessage(x),x);}
    }
    private Sheet resolveSheet(Workbook w){
        for(int i=0;i<w.getNumberOfSheets();i++)if(w.getSheetAt(i).getSheetName().equals("Details"))return w.getSheetAt(i);
        for(int i=0;i<w.getNumberOfSheets();i++)if(norm(w.getSheetAt(i).getSheetName()).contains("detail"))return w.getSheetAt(i);
        if(w.getNumberOfSheets()==0)throw new ImportException("Workbook contains no sheets."); return w.getSheetAt(0);
    }
    private int findHeaderRow(Sheet s,DataFormatter f,FormulaEvaluator e){for(int i=0;i<=Math.min(s.getLastRowNum(),19);i++){Row r=s.getRow(i);if(r!=null)for(Cell c:r)if(norm(cell(c,f,e)).equals("fixedassetname"))return i;}return -1;}
    private Columns columns(Row h,DataFormatter f,FormulaEvaluator e){return new Columns(
        col(h,f,e,"fI","fi","Fixed Asset Code","Code"),col(h,f,e,"FixedAssetName"),col(h,f,e,"PhysicalCount"),col(h,f,e,"FAType"),col(h,f,e,"KindExpense"),col(h,f,e,"KindFixedAsset"),col(h,f,e,"DateStart"),col(h,f,e,"Histoty Cost","History Cost"),col(h,f,e,"NetBookValue"),col(h,f,e,"YearDepreciation"),col(h,f,e,"Group currently using"),col(h,f,e,"Email's Checked","Emails Checked"),col(h,f,e,"Email's Approved","Emails Approved"),col(h,f,e,"Div"),col(h,f,e,"Factory"),col(h,f,e,"Floor"),col(h,f,e,"#A"),col(h,f,e,"#AA"),col(h,f,e,"#AAA"),col(h,f,e,"InvoiceNo"),col(h,f,e,"Maker"),col(h,f,e,"PurchasedFrom"),col(h,f,e,"Type"),col(h,f,e,"SerialNo"),col(h,f,e,"Check METI"),col(h,f,e,"Photo1"),col(h,f,e,"Photo2"),col(h,f,e,"Photo3"),col(h,f,e,"Đánh giá hình","Danh gia hinh"),col(h,f,e,"MachineStatus"));}
    private int col(Row h,DataFormatter f,FormulaEvaluator e,String... ns){Set<String>w=new HashSet<>();for(String n:ns)w.add(norm(n));for(int i=0;i<Math.max(0,h.getLastCellNum());i++)if(w.contains(norm(cell(h.getCell(i),f,e))))return i;return -1;}
    private String text(Row r,int i,DataFormatter f,FormulaEvaluator e){if(r==null||i<0)return null;String v=cell(r.getCell(i),f,e).trim();return v.isEmpty()?null:v;}
    private String cell(Cell c,DataFormatter f,FormulaEvaluator e){if(c==null)return "";try{return f.formatCellValue(c,e);}catch(Exception x){return f.formatCellValue(c);}}
    private BigDecimal decimal(Row r,int i,DataFormatter f,FormulaEvaluator e){String v=text(r,i,f,e);if(v==null)return null;try{return new BigDecimal(v.replace(",",""));}catch(Exception x){return null;}}
    private LocalDateTime date(Row r,int i,DataFormatter f,FormulaEvaluator e){if(r==null||i<0)return null;Cell c=r.getCell(i);if(c==null)return null;try{if(c.getCellType()==CellType.NUMERIC&&DateUtil.isCellDateFormatted(c))return c.getLocalDateTimeCellValue();}catch(Exception ignored){}String v=cell(c,f,e).trim();try{return LocalDateTime.parse(v,DATE_TIME);}catch(Exception ignored){return null;}}
    private String norm(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    private String rootMessage(Throwable t){while(t.getCause()!=null)t=t.getCause();return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();}
    private record Columns(int machineCode,int faName,int physicalCount,int faType,int kindExpense,int kindFixedAsset,int dateStart,int histotyCost,int netBookValue,int yearDepreciation,int group,int emailChecked,int emailApproved,int div,int factory,int floor,int positionA,int positionAA,int positionAAA,int invoiceNo,int maker,int purchasedFrom,int type,int serialNo,int checkMETI,int photo1,int photo2,int photo3,int photoJudge,int machineStatus){
        Set<String> present(){String[]n={"MachineCode","FAName","PhysicalCount","FAType","KindExpense","KindFixedAsset","DateStart","HistotyCost","NetBookValue","YearDepreciation","Group","EmailChecked","EmailApproved","Div","Factory","Floor","PositionA","PositionAA","PositionAAA","InvoiceNo","Maker","PurchasedFrom","Type","SerialNo","CheckMETI","Photo1","Photo2","Photo3","PhotoJudge","MachineStatus"};int[]v={machineCode,faName,physicalCount,faType,kindExpense,kindFixedAsset,dateStart,histotyCost,netBookValue,yearDepreciation,group,emailChecked,emailApproved,div,factory,floor,positionA,positionAA,positionAAA,invoiceNo,maker,purchasedFrom,type,serialNo,checkMETI,photo1,photo2,photo3,photoJudge,machineStatus};Set<String>x=new LinkedHashSet<>();for(int i=0;i<n.length;i++)if(v[i]>=0)x.add(n[i]);return Set.copyOf(x);}}
}
