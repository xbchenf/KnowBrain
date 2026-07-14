package com.knowbrain.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.nio.file.*;
import java.util.List;

/**
 * 测试文档生成器 — 为所有支持的文档格式生成真实测试数据
 *
 * <p>用法：直接运行 main()，在 src/test/resources/test-docs/ 下生成测试文件
 *
 * <p>覆盖格式：pdf / docx / xlsx / pptx / txt / md / csv
 */
public class TestDataGenerator {

    private static final Path OUTPUT_DIR = Paths.get(
            "src/test/resources/test-docs");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT_DIR);

        generatePdf("企业信息安全管理制度.pdf");
        generateDocx("新员工入职指南.docx");
        generateXlsx("2024年IT设备采购清单.xlsx");
        generatePptx("Q2技术架构升级方案.pptx");
        generateTxt("VPN配置说明.txt");
        generateMd("API接口文档.md");
        generateCsv("部门通讯录.csv");

        System.out.println("=== 测试文档生成完成 ===");
        Files.list(OUTPUT_DIR).forEach(p ->
                System.out.printf("  %s (%,d bytes)\n",
                        p.getFileName(), p.toFile().length()));
    }

    // ==================== PDF ====================

    static void generatePdf(String filename) throws Exception {
        Path path = OUTPUT_DIR.resolve(filename);
        try (PDDocument doc = new PDDocument()) {

            // --- 封面 ---
            PDPage cover = new PDPage(PDRectangle.A4);
            doc.addPage(cover);
            try (PDPageContentStream cs = new PDPageContentStream(doc, cover)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
                cs.newLineAtOffset(100, 700);
                cs.showText("Qi Ye Xin Xi An Quan Guan Li Zhi Du");
                cs.endText();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                cs.newLineAtOffset(100, 640);
                cs.showText("Version: V3.2  |  Effective: 2024-01-01");
                cs.endText();

                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 560);
                cs.showText("Confidentiality Level: Internal");
                cs.endText();
            }

            // --- 第 2 页：概述 ---
            PDPage p2 = new PDPage(PDRectangle.A4);
            doc.addPage(p2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p2)) {
                float y = 750;
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                cs.newLineAtOffset(72, y);
                cs.showText("1. Overview and Scope");
                cs.endText();

                y -= 40;
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(72, y);
                cs.showText("This policy applies to all employees, contractors, and third-party vendors who");
                cs.endText();
                y -= 18;
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(72, y);
                cs.showText("access, process, or store company information assets. Coverage includes:");
                cs.endText();

                y -= 30;
                for (String item : List.of(
                        "- On-premise servers and network equipment in Beijing/Shanghai data centers",
                        "- Cloud workloads on AWS (us-east-1) and Alibaba Cloud (cn-beijing)",
                        "- End-user devices: laptops, mobile phones, USB drives",
                        "- All software systems including ERP, CRM, HRIS, and internal tools",
                        "- Physical access to office areas, server rooms, and backup facilities"
                )) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    cs.newLineAtOffset(90, y);
                    cs.showText(item);
                    cs.endText();
                    y -= 20;
                }

                y -= 20;
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.newLineAtOffset(72, y);
                cs.showText("1.1 Asset Classification Levels");
                cs.endText();

                // 简单表格
                y -= 30;
                float[] colX = {72, 200, 350};
                String[][] rows = {
                        {"L1-Public", "Marketing materials, press releases", "No restriction"},
                        {"L2-Internal", "Policies, meeting notes, org charts", "Employee login required"},
                        {"L3-Confidential", "Financials, PII, source code", "Explicit ACL + audit log"},
                        {"L4-Restricted", "Trade secrets, M&A data", "Encrypted + DLP + MFA"},
                };
                for (String[] row : rows) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
                    cs.newLineAtOffset(colX[0], y);
                    cs.showText(row[0]);
                    cs.endText();
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    cs.newLineAtOffset(colX[1], y);
                    cs.showText(row[1]);
                    cs.endText();
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    cs.newLineAtOffset(colX[2], y);
                    cs.showText(row[2]);
                    cs.endText();
                    y -= 20;
                }
            }

            // --- 第 3 页：密码策略 ---
            PDPage p3 = new PDPage(PDRectangle.A4);
            doc.addPage(p3);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p3)) {
                float y = 750;
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                cs.newLineAtOffset(72, y);
                cs.showText("2. Password and Access Control Policy");
                cs.endText();

                y -= 40;
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(72, y);
                cs.showText("Minimum password length: 12 characters. Must contain at least 3 of the");
                cs.endText();
                y -= 18;
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(72, y);
                cs.showText("following categories: uppercase letters, lowercase letters, digits, symbols.");
                cs.endText();
                y -= 18;
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                cs.newLineAtOffset(72, y);
                cs.showText("Password must be changed every 90 days. Reuse of last 5 passwords is prohibited.");
                cs.endText();

                y -= 30;
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                cs.newLineAtOffset(72, y);
                cs.showText("2.1 MFA Requirements");
                cs.endText();

                y -= 25;
                for (String item : List.of(
                        "All remote access (VPN, SSH, RDP) requires MFA (TOTP or FIDO2 hardware key)",
                        "Admin/root account access requires phishing-resistant MFA (FIDO2 WebAuthn)",
                        "MFA bypass requests must be approved by CISO and logged for audit",
                        "SMS-based MFA is permitted only as a transitional measure until 2024-Q2"
                )) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    cs.newLineAtOffset(90, y);
                    cs.showText(item);
                    cs.endText();
                    y -= 20;
                }
            }

            // 在 try-with-resources 关闭前写出字节
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            doc.save(bos);
            Files.write(path, bos.toByteArray());
        }
    }

    // ==================== DOCX ====================

    static void generateDocx(String filename) throws Exception {
        Path path = OUTPUT_DIR.resolve(filename);
        try (XWPFDocument doc = new XWPFDocument()) {

            // Heading 1
            XWPFParagraph h1 = doc.createParagraph();
            h1.setStyle("Heading1");
            XWPFRun h1r = h1.createRun();
            h1r.setText("Xin Yuan Gong Ru Zhi Zhi Nan");
            h1r.setBold(true);
            h1r.setFontSize(22);

            // Subtitle
            XWPFParagraph sub = doc.createParagraph();
            XWPFRun subr = sub.createRun();
            subr.setText("Welcome to the team! This guide covers your first week checklist.");
            subr.setFontSize(12);
            subr.setItalic(true);

            // Heading 2
            XWPFParagraph h2 = doc.createParagraph();
            h2.setStyle("Heading2");
            XWPFRun h2r = h2.createRun();
            h2r.setText("1. Pre-boarding Checklist (HR to complete 3 days before Day 1)");
            h2r.setBold(true);
            h2r.setFontSize(16);

            for (String item : List.of(
                    "Prepare laptop (standard spec: ThinkPad X1 Carbon, 32GB RAM, 1TB SSD)",
                    "Create accounts: SSO (Okta), Email (Office 365), Slack, GitHub Enterprise",
                    "Assign buddy: a peer from the same team, notify both by email",
                    "Order access card: request building pass from Security desk at Floor 3",
                    "Send welcome kit: company handbook (PDF), team org chart, parking map"
            )) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText("  • " + item);
                r.setFontSize(11);
            }

            // Heading 2
            XWPFParagraph h2b = doc.createParagraph();
            h2b.setStyle("Heading2");
            XWPFRun h2br = h2b.createRun();
            h2br.setText("2. Day 1 Schedule");
            h2br.setBold(true);
            h2br.setFontSize(16);

            // Table
            XWPFTable table = doc.createTable(6, 3);
            String[][] tableData = {
                    {"Time", "Activity", "Location / Owner"},
                    {"09:00–09:30", "Badge pickup + building tour", "Lobby, Security Desk"},
                    {"09:30–10:30", "IT setup: laptop, VPN, printer", "IT Helpdesk, Room 302"},
                    {"10:30–12:00", "HR orientation + benefits enrollment", "HR Office, Floor 4"},
                    {"13:00–14:00", "Team lunch with buddy", "Cafeteria, B1"},
                    {"14:00–17:00", "Meet the team + project walkthrough", "Team Area, Floor 5"},
            };
            for (int i = 0; i < tableData.length; i++) {
                XWPFTableRow row = table.getRow(i);
                for (int j = 0; j < tableData[i].length; j++) {
                    XWPFTableCell cell = row.getCell(j);
                    cell.setText(tableData[i][j]);
                    if (i == 0) {
                        cell.getParagraphs().get(0).getRuns().get(0).setBold(true);
                    }
                }
            }

            // Heading 2
            XWPFParagraph h2c = doc.createParagraph();
            h2c.setStyle("Heading2");
            XWPFRun h2cr = h2c.createRun();
            h2cr.setText("3. VPN Configuration");
            h2cr.setBold(true);
            h2cr.setFontSize(16);

            XWPFParagraph vpn = doc.createParagraph();
            XWPFRun vpnr = vpn.createRun();
            vpnr.setText("Download the OpenConnect client from intranet portal. Server address: "
                    + "vpn.company.com:443. Protocol: AnyConnect. Authentication: SSO (Okta) + "
                    + "TOTP token. After first login, save the profile for auto-connect on startup. "
                    + "If you encounter certificate warnings, install the internal CA root certificate "
                    + "from https://pki.company.com/root-ca.cer before connecting.");
            vpnr.setFontSize(11);

            // Heading 3 — 子节
            XWPFParagraph h3 = doc.createParagraph();
            h3.setStyle("Heading3");
            XWPFRun h3r = h3.createRun();
            h3r.setText("3.1 Troubleshooting Common VPN Issues");
            h3r.setBold(true);
            h3r.setFontSize(13);

            for (String item : List.of(
                    "Error \"Auth failed\": verify your TOTP code is current (30-second window)",
                    "Error \"No route to host\": check firewall — TCP/443 must be open outbound",
                    "Connected but no internet: run `ipconfig /flushdns` and reconnect",
                    "Frequent disconnects: switch from Wi-Fi to wired Ethernet for stability"
            )) {
                XWPFParagraph p = doc.createParagraph();
                XWPFRun r = p.createRun();
                r.setText("  • " + item);
                r.setFontSize(11);
            }

            // Heading 2
            XWPFParagraph h2d = doc.createParagraph();
            h2d.setStyle("Heading2");
            XWPFRun h2dr = h2d.createRun();
            h2dr.setText("4. Key Contacts");
            h2dr.setBold(true);
            h2dr.setFontSize(16);

            XWPFTable contactTable = doc.createTable(5, 3);
            String[][] contacts = {
                    {"Role", "Name", "Extension / Email"},
                    {"HR Business Partner", "Zhang Wei", "x8888 / hr-bp@company.com"},
                    {"IT Helpdesk", "N/A (ticket system)", "https://helpdesk.company.com"},
                    {"Buddy", "Assigned via email", "Check your welcome email"},
                    {"Office Manager", "Li Fang", "x8001 / office@company.com"},
            };
            for (int i = 0; i < contacts.length; i++) {
                XWPFTableRow row = contactTable.getRow(i);
                for (int j = 0; j < contacts[i].length; j++) {
                    row.getCell(j).setText(contacts[i][j]);
                    if (i == 0) {
                        row.getCell(j).getParagraphs().get(0).getRuns().get(0).setBold(true);
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                doc.write(fos);
            }
        }
    }

    // ==================== XLSX ====================

    static void generateXlsx(String filename) throws Exception {
        Path path = OUTPUT_DIR.resolve(filename);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {

            // --- Sheet 1: 采购总表 ---
            var sheet1 = wb.createSheet("Purchase Orders");
            String[][] headers1 = {
                    {"PO#", "Item Description", "Category", "Qty", "Unit Price (CNY)", "Total (CNY)", "Department", "Status", "Order Date"},
                    {"PO-2024-001", "Dell PowerEdge R750xs Server", "Server", "4", "85,000", "340,000", "Infrastructure", "Delivered", "2024-01-15"},
                    {"PO-2024-002", "ThinkPad X1 Carbon Gen 11", "Laptop", "20", "12,500", "250,000", "Engineering", "Delivered", "2024-01-20"},
                    {"PO-2024-003", "Cisco Catalyst 9300 Switch", "Network", "6", "28,000", "168,000", "Infrastructure", "In Transit", "2024-02-01"},
                    {"PO-2024-004", "Dell UltraSharp U2723QE Monitor", "Display", "30", "3,200", "96,000", "Engineering", "Delivered", "2024-02-10"},
                    {"PO-2024-005", "Synology RS3621xs+ NAS (48TB)", "Storage", "2", "65,000", "130,000", "Infrastructure", "Pending Approval", "2024-03-05"},
                    {"PO-2024-006", "APC Smart-UPS 3000VA", "Power", "8", "6,800", "54,400", "Infrastructure", "Delivered", "2024-03-12"},
                    {"PO-2024-007", "NVIDIA RTX 6000 Ada 48GB", "GPU", "4", "48,000", "192,000", "AI Lab", "In Transit", "2024-03-20"},
                    {"PO-2024-008", "Logitech MX Keys + MX Master 3S", "Peripheral", "50", "800", "40,000", "All Depts", "Delivered", "2024-04-01"},
                    {"PO-2024-009", "Ubiquiti UniFi 6 Enterprise AP", "WiFi", "15", "2,400", "36,000", "Infrastructure", "Pending Approval", "2024-04-10"},
                    {"PO-2024-010", "iPad Pro 12.9\" for Exec Team", "Tablet", "5", "8,999", "44,995", "Management", "Approved", "2024-04-15"},
            };

            for (int r = 0; r < headers1.length; r++) {
                var row = sheet1.createRow(r);
                for (int c = 0; c < headers1[r].length; c++) {
                    var cell = row.createCell(c);
                    cell.setCellValue(headers1[r][c]);
                    if (r == 0) {
                        var style = wb.createCellStyle();
                        var font = wb.createFont();
                        font.setBold(true);
                        style.setFont(font);
                        cell.setCellStyle(style);
                    }
                }
            }

            // 列宽
            int[] widths1 = {4000, 9000, 3500, 2000, 5000, 4000, 5000, 5000, 4000};
            for (int i = 0; i < widths1.length; i++) {
                sheet1.setColumnWidth(i, widths1[i]);
            }

            // --- Sheet 2: 按部门汇总 ---
            var sheet2 = wb.createSheet("Department Summary");
            String[][] headers2 = {
                    {"Department", "PO Count", "Total Amount (CNY)", "Delivered", "Pending"},
                    {"Infrastructure", "5", "728,400", "3", "2"},
                    {"Engineering", "2", "346,000", "2", "0"},
                    {"AI Lab", "1", "192,000", "0", "1"},
                    {"Management", "1", "44,995", "0", "1"},
                    {"All Depts", "1", "40,000", "1", "0"},
                    {"", "", "", "", ""},
                    {"Grand Total", "10", "1,351,395", "6", "4"},
            };
            for (int r = 0; r < headers2.length; r++) {
                var row = sheet2.createRow(r);
                for (int c = 0; c < headers2[r].length; c++) {
                    var cell = row.createCell(c);
                    cell.setCellValue(headers2[r][c]);
                    if (r == 0 || r == headers2.length - 1) {
                        var style = wb.createCellStyle();
                        var font = wb.createFont();
                        font.setBold(true);
                        style.setFont(font);
                        cell.setCellStyle(style);
                    }
                }
            }
            int[] widths2 = {5000, 3500, 5500, 3500, 3500};
            for (int i = 0; i < widths2.length; i++) {
                sheet2.setColumnWidth(i, widths2[i]);
            }

            // 合并单元格: Grand Total 行的前两列
            sheet2.addMergedRegion(
                    new org.apache.poi.ss.util.CellRangeAddress(7, 7, 0, 1));

            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                wb.write(fos);
            }
        }
    }

    // ==================== PPTX ====================

    static void generatePptx(String filename) throws Exception {
        Path path = OUTPUT_DIR.resolve(filename);
        try (XMLSlideShow ppt = new XMLSlideShow()) {

            // --- Slide 1: Title ---
            XSLFSlide slide1 = ppt.createSlide();
            XSLFTextBox titleBox = slide1.createTextBox();
            titleBox.setAnchor(new java.awt.Rectangle(80, 200, 600, 80));
            XSLFTextParagraph tp1 = titleBox.addNewTextParagraph();
            XSLFTextRun tr1 = tp1.addNewTextRun();
            tr1.setText("Q2 Ji Shu Jia Gou Sheng Ji Fang An");
            tr1.setFontSize(32.0);
            tr1.setBold(true);

            XSLFTextBox subBox = slide1.createTextBox();
            subBox.setAnchor(new java.awt.Rectangle(80, 300, 600, 60));
            XSLFTextParagraph sp1 = subBox.addNewTextParagraph();
            XSLFTextRun sr1 = sp1.addNewTextRun();
            sr1.setText("Proposed by: Infrastructure Team  |  Date: 2024-03-01");
            sr1.setFontSize(16.0);

            // --- Slide 2: Background & Motivation ---
            XSLFSlide slide2 = ppt.createSlide();
            XSLFTextBox s2t = slide2.createTextBox();
            s2t.setAnchor(new java.awt.Rectangle(50, 40, 620, 50));
            XSLFTextParagraph s2tp = s2t.addNewTextParagraph();
            XSLFTextRun s2tr = s2tp.addNewTextRun();
            s2tr.setText("Current Pain Points");
            s2tr.setFontSize(28.0);
            s2tr.setBold(true);

            XSLFTextBox s2b = slide2.createTextBox();
            s2b.setAnchor(new java.awt.Rectangle(80, 120, 560, 300));
            XSLFTextParagraph s2bp = s2b.addNewTextParagraph();
            String[] bullets = {
                    "Monolithic Spring Boot 2.7 app — deployment takes 12 minutes, cannot scale per-module",
                    "MySQL 5.7 single instance — no HA, 45-minute RTO after crash in Jan 2024 incident",
                    "Elasticsearch 7.x cluster overloaded — 80% CPU at peak, query timeout 30% of requests",
                    "Manual CI/CD (Jenkins freestyle jobs) — no canary, no rollback, 3 deploy incidents in Q1",
                    "Logs scattered across 8 EC2 instances — grep-based troubleshooting takes 30+ min avg"
            };
            for (String b : bullets) {
                XSLFTextParagraph p = s2b.addNewTextParagraph();
                p.setBullet(true);
                XSLFTextRun r = p.addNewTextRun();
                r.setText(b);
                r.setFontSize(14.0);
            }

            // --- Slide 3: Architecture (with table) ---
            XSLFSlide slide3 = ppt.createSlide();
            XSLFTextBox s3t = slide3.createTextBox();
            s3t.setAnchor(new java.awt.Rectangle(50, 40, 620, 50));
            XSLFTextParagraph s3tp = s3t.addNewTextParagraph();
            XSLFTextRun s3tr = s3tp.addNewTextRun();
            s3tr.setText("Technology Stack Comparison");
            s3tr.setFontSize(28.0);
            s3tr.setBold(true);

            // Table
            XSLFTable table = slide3.createTable(6, 4);
            table.setAnchor(new java.awt.Rectangle(50, 110, 620, 300));

            String[][] tbl = {
                    {"Component", "Current", "Proposed", "Benefit"},
                    {"Backend", "Spring Boot 2.7", "Spring Boot 3.3 + GraalVM", "Startup < 1s, 50% less memory"},
                    {"Database", "MySQL 5.7 Single", "MySQL 8.0 InnoDB Cluster", "RTO < 30s, RPO = 0"},
                    {"Cache", "Redis 6 Single", "Redis 7 Cluster (3 nodes)", "99.99% availability"},
                    {"Search", "Elasticsearch 7.x", "Elasticsearch 8.x + BM25", "40% better relevance"},
                    {"Deploy", "Jenkins Freestyle", "GitHub Actions + ArgoCD", "Canary + auto-rollback"},
            };
            for (int r = 0; r < tbl.length; r++) {
                XSLFTableRow row = table.getRows().get(r);
                for (int c = 0; c < tbl[r].length; c++) {
                    XSLFTableCell cell = row.getCells().get(c);
                    cell.setText(tbl[r][c]);
                    if (r == 0) {
                        cell.getTextParagraphs().get(0).getTextRuns().get(0).setBold(true);
                    }
                }
            }

            // --- Slide 4: Migration Plan ---
            XSLFSlide slide4 = ppt.createSlide();
            XSLFTextBox s4t = slide4.createTextBox();
            s4t.setAnchor(new java.awt.Rectangle(50, 40, 620, 50));
            XSLFTextParagraph s4tp = s4t.addNewTextParagraph();
            XSLFTextRun s4tr = s4tp.addNewTextRun();
            s4tr.setText("Migration Phases (8 Weeks)");
            s4tr.setFontSize(28.0);
            s4tr.setBold(true);

            XSLFTextBox s4b = slide4.createTextBox();
            s4b.setAnchor(new java.awt.Rectangle(80, 120, 560, 350));
            String[] phases = {
                    "Phase 1 (Week 1-2): MySQL 8.0 upgrade + InnoDB Cluster setup — run in parallel, cutover on weekend",
                    "Phase 2 (Week 3-4): Spring Boot 3.3 migration — javax → jakarta, Spring Security 6, virtual threads",
                    "Phase 3 (Week 5-6): Elasticsearch 8.x upgrade + index reindexing — zero-downtime with aliases",
                    "Phase 4 (Week 7): Redis Cluster deployment + session migration — rolling restart, no session loss",
                    "Phase 5 (Week 8): CI/CD pipeline overhaul — GitHub Actions + ArgoCD + Datadog monitoring"
            };
            for (String p : phases) {
                XSLFTextParagraph pp = s4b.addNewTextParagraph();
                pp.setBullet(true);
                XSLFTextRun rr = pp.addNewTextRun();
                rr.setText(p);
                rr.setFontSize(14.0);
            }

            try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                ppt.write(fos);
            }
        }
    }

    // ==================== TXT ====================

    static void generateTxt(String filename) throws Exception {
        Path path = OUTPUT_DIR.resolve(filename);
        String content = """
                ===========================================
                VPN Configuration Guide (Internal Document)
                ===========================================

                1. Prerequisites
                ----------------
                Before configuring VPN, ensure you have:
                - Company-issued laptop with Windows 10/11 or macOS 13+
                - Valid SSO account (Okta) with MFA enrolled
                - Internet connection (corporate Wi-Fi or home broadband)

                2. Step-by-Step Setup
                ----------------------
                Step 1: Download the VPN client
                  - Windows: https://intranet.company.com/software/openconnect-win64.msi
                  - macOS:   https://intranet.company.com/software/openconnect-mac.dmg
                  - Linux:   `apt install openconnect` (Ubuntu) / `yum install openconnect` (CentOS)

                Step 2: Install and launch
                  - Windows: run the MSI installer as Administrator, reboot if prompted
                  - macOS: drag to Applications, approve in System Preferences > Security
                  - First launch: click the tray icon → "New Profile"

                Step 3: Configure the profile
                  Profile Name: Company VPN
                  Server:       vpn.company.com:443
                  Protocol:     AnyConnect
                  Auth Method:  SSO (Okta) + TOTP

                Step 4: Connect
                  - Click "Connect" and complete Okta login in the browser popup
                  - Enter your 6-digit TOTP code when prompted
                  - The tray icon turns green when connected

                3. Troubleshooting
                ------------------
                Problem: "Authentication Failed"
                  → Verify your TOTP code is current (30-second validity window)
                  → Sync your authenticator app time via Settings

                Problem: "Certificate Not Trusted"
                  → Download and install internal CA: https://pki.company.com/root-ca.cer
                  → Windows: double-click → "Install Certificate" → "Trusted Root CA"
                  → macOS: open Keychain Access → drag cert to "System"

                Problem: Connected but no internal websites load
                  → Run: ipconfig /flushdns (Windows) or dscacheutil -flushcache (macOS)
                  → Check proxy settings: no proxy for *.company.com
                  → If still broken, open ticket at https://helpdesk.company.com

                Problem: Frequent disconnects every 5-10 minutes
                  → Switch from Wi-Fi to wired Ethernet
                  → Ensure MTU is 1400 or lower (some ISP routers fragment large packets)
                  → Update VPN client to latest version

                4. Support
                ----------
                IT Helpdesk Portal:  https://helpdesk.company.com
                Internal Extension:   x5555 (Mon-Fri 9:00-18:00 CST)
                Emergency After-hours: +86-10-8888-7777 (on-call engineer)
                """;
        Files.writeString(path, content);
    }

    // ==================== Markdown ====================

    static void generateMd(String filename) throws Exception {
        Path path = OUTPUT_DIR.resolve(filename);
        String content = """
                # Internal API Reference — User Service v2.3

                > **Base URL**: `https://api.internal.company.com/v2`
                > **Auth**: Bearer token (JWT, issued by SSO)
                > **Content-Type**: `application/json; charset=utf-8`

                ---

                ## GET /users/{id}

                Retrieve a single user by employee ID.

                ### Path Parameters

                | Parameter | Type   | Required | Description           |
                |-----------|--------|----------|-----------------------|
                | id        | string | yes      | Employee ID (6-digit) |

                ### Query Parameters

                | Parameter | Type   | Default | Description                        |
                |-----------|--------|---------|------------------------------------|
                | expand    | string | none    | Comma-separated: `dept`, `manager` |

                ### Response `200 OK`

                ```json
                {
                  "id": "E12345",
                  "name": "Zhang Wei",
                  "email": "zhangwei@company.com",
                  "department": {
                    "id": "D009",
                    "name": "Infrastructure Engineering"
                  },
                  "manager": {
                    "id": "E10001",
                    "name": "Liu Qiang"
                  },
                  "status": "active",
                  "created_at": "2022-03-15T09:30:00Z"
                }
                ```

                ### Error Codes

                | Status | Code              | Meaning                        |
                |--------|-------------------|--------------------------------|
                | 404    | USER_NOT_FOUND    | Employee ID does not exist     |
                | 403    | INSUFFICIENT_SCOPE| Token lacks `user:read` scope  |
                | 410    | USER_DEPROVISIONED| User was offboarded            |

                ---

                ## POST /users/search

                Full-text search across employee directory.

                ### Request Body

                ```json
                {
                  "query": "infrastructure engineer beijing",
                  "filters": {
                    "department": ["D009", "D010"],
                    "status": "active",
                    "joined_after": "2023-01-01"
                  },
                  "pagination": {
                    "page": 1,
                    "size": 20
                  }
                }
                ```

                ### Performance Notes

                - Search latency SLA: p95 < 200ms for result sets under 1,000 users
                - For full directory export, use `GET /users/export` with async callback
                - Rate limit: 100 requests/minute per API key (429 on exceed)

                ### Example: cURL

                ```bash
                curl -X POST https://api.internal.company.com/v2/users/search \\
                  -H "Authorization: Bearer $API_TOKEN" \\
                  -H "Content-Type: application/json" \\
                  -d '{"query": "data engineer", "pagination": {"page": 1, "size": 5}}'
                ```

                ---

                ## Rate Limiting

                All endpoints enforce token-bucket rate limiting:

                | Tier   | Burst | Sustained (req/s) | Scope       |
                |--------|-------|--------------------|-------------|
                | admin  | 100   | 20                 | per user    |
                | app    | 50    | 10                 | per API key |
                | basic  | 10    | 2                  | per IP      |

                When rate-limited, the API returns `429 Too Many Requests` with a
                `Retry-After` header indicating the number of seconds to wait.
                """;
        Files.writeString(path, content);
    }

    // ==================== CSV ====================

    static void generateCsv(String filename) throws Exception {
        Path path = OUTPUT_DIR.resolve(filename);
        String content = """
                Department,Name,Email,Extension,Location,Role,Status
                Infrastructure,Zhang Wei,zhangwei@company.com,x8888,Building A Floor 3,Director,Active
                Infrastructure,Wang Fang,wangfang@company.com,x8889,Building A Floor 3,Sr. Network Engineer,Active
                Infrastructure,Li Ming,liming@company.com,x8890,Building A Floor 3,Systems Administrator,Active
                Infrastructure,Chen Jie,chenjie@company.com,x8891,Building A Floor 3,DevOps Engineer,Active
                Infrastructure,Huang Li,huangli@company.com,x8892,Building A Floor 3,DBA,On Leave
                Engineering,Li Qiang,liqiang@company.com,x9001,Building B Floor 5,VP Engineering,Active
                Engineering,Zhao Yun,zhaoyun@company.com,x9002,Building B Floor 5,Backend Lead,Active
                Engineering,Sun Yan,sunyan@company.com,x9003,Building B Floor 5,Frontend Lead,Active
                Engineering,Zhou Tao,zhoutao@company.com,x9004,Building B Floor 5,QA Manager,Active
                Engineering,Wu Jing,wujing@company.com,x9005,Building B Floor 5,Sr. SRE,Active
                HR,Xu Mei,xumei@company.com,x8001,Building A Floor 4,HR Director,Active
                HR,Ma Lin,malin@company.com,x8002,Building A Floor 4,HRBP (Engineering),Active
                HR,Liu Yang,liuyang@company.com,x8003,Building A Floor 4,Recruiter,Active
                Finance,He Qian,heqian@company.com,x7001,Building A Floor 6,CFO,Active
                Finance,Lin Xiao,linxiao@company.com,x7002,Building A Floor 6,Accountant,Active
                Finance,Peng Bo,pengbo@company.com,x7003,Building A Floor 6,Financial Analyst,Active
                Management,Guo Feng,guofeng@company.com,x6001,Building A Floor 8,CEO,Active
                Management,Tan Rui,tanrui@company.com,x6002,Building A Floor 8,CTO,Active
                Management,Deng Hua,denghua@company.com,x6003,Building A Floor 8,COO,Active
                AI Lab,Cao Yang,caoyang@company.com,x9501,Building C Floor 2,Lead Researcher,Active
                AI Lab,Shen Wei,shenwei@company.com,x9502,Building C Floor 2,ML Engineer,Active
                AI Lab,Yuan Bo,yuanbo@company.com,x9503,Building C Floor 2,Data Scientist,Active
                AI Lab,Jiang Tao,jiangtao@company.com,x9504,Building C Floor 2,NLP Engineer,Active
                """;
        Files.writeString(path, content);
    }

    // ==================== 工具方法 ====================

    /** PDDocument → byte[] */
    static byte[] toByteArray(PDDocument doc) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.save(bos);
        return bos.toByteArray();
    }
}
