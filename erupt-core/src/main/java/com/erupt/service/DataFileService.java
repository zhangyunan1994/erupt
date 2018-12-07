package com.erupt.service;

import com.erupt.annotation.sub_field.View;
import com.erupt.model.core.EruptFieldModel;
import com.erupt.model.core.EruptModel;
import com.erupt.util.HttpUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Created by liyuepeng on 12/4/18.
 */
@Service
public class DataFileService {

    public void exportExcel(EruptModel eruptModel, HttpServletResponse response) {
        Workbook wb = new HSSFWorkbook();
        Sheet sheet = wb.createSheet(eruptModel.getErupt().name());
        Row row = sheet.createRow(0);
        int colNum = 0;
        for (EruptFieldModel fieldModel : eruptModel.getEruptFieldModels()) {
            for (View view : fieldModel.getEruptField().views()) {
                if (view.show()) {
                    Cell cell = row.createCell(colNum++);
                    cell.setCellValue(view.title());
                }

            }
        }
        try {
            wb.write(HttpUtil.downLoadField(response, eruptModel.getErupt().name() + ".erupt.xls"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
