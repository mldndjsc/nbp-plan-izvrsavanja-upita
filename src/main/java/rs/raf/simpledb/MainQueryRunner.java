package rs.raf.simpledb;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import rs.raf.simpledb.parse.QueryData;
import rs.raf.simpledb.query.*;
import rs.raf.simpledb.query.aggregation.AggregationFn;
import rs.raf.simpledb.query.aggregation.CountFn;
import rs.raf.simpledb.query.aggregation.GroupByPlan;
import rs.raf.simpledb.query.operators.Scan;
import rs.raf.simpledb.record.Schema;
import rs.raf.simpledb.tx.Transaction;


public class MainQueryRunner {


    public static void main(String[] args) {
        try {

            boolean isnew = initStudentDB();

            if (isnew) {
                InitKolokvijumDB.createDBTables();
                InitKolokvijumDB.genericInsertDBData();
            }else
                System.out.println("StudentDB is already created!");

			/*
			Šema baze je sledeca:

			STUDENT(sid int, sname varchar(25), smerid int, goddipl int)
			SMER(smid int, smerName varchar(25))
			PREDMET(pid int, naziv varchar(25), smerid int)
			*/

            //queryManualPlan1();

            //queryManualPlan2();

            //querySQL1();
            //querySQL2();
            //queryManualPlan3();
            queryManualPlan3Optimization();

        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static void queryManualPlan1() {

		/*
		 * Ovaj primer pokazuje manuelno kreiranje plana izvrsavanja upita.
		 * Zelimo da prikazemo sve studente koji su diplomirali 2025. godine.
		 * Ekvivalentan SQL upit bi bio:
		 * 	SELECT sname, goddipl
	       	FROM STUDENT
	        WHERE goddipl=2025

	        Obratiti paznju kako se kreiraju operatori Selekcije i Projekcije
	  	 *
		 */
        Transaction tx = new Transaction();
        Plan p1 = new TablePlan("student", tx);


        // Kreiranje predikatskog uslova 'goddipl=2025'
        Expression lhs1 = new FieldNameExpression("goddipl");
        Constant c = new IntConstant(2025);
        Expression rhs1 = new ConstantExpression(c);
        Term t1 = new Term(lhs1, rhs1);
        Predicate pred1 = new Predicate(t1); //goddipl=2025  (SI)

        Plan p3 = new SelectionPlan(p1, pred1);


        List<String> fields = Arrays.asList("sname", "goddipl");
        Plan p4 = new ProjectionPlan(p3, fields);

        Scan s = p4.open();

        System.out.println("\nStudenti sa smera Softversko inzenjerstvo:");
        System.out.println("\nStudent\t\t\tGodina diplomiranja");
        System.out.println("-----------------------------------------");
        while (s.next()) {
            String sname = s.getString("sname"); 	//SimpleDB cuva naziv kolona
            int god = s.getInt("goddipl"); 			//sa malim slovima (lower case)
            System.out.println(sname + "\t\t" + god);
        }
        System.out.println();
        s.close();
        tx.commit();

    }

    private static void queryManualPlan2() {

		/*
		 * Ovaj primer pokazuje manuelno kreiranje plana izvrsavanja upita.
		 * Zelimo da prikazemo sve studente sa smera Softversko inzenjerstvo.
		 * Ekvivalentan SQL upit bi bio:
		 * 	SELECT sname, smerName
	       	FROM STUDENT, SMER
	        WHERE smerId = SMId
	        AND	smerId = 1

	        Obratiti paznju da se equi-join izvodi kao:
	        SELEKCIJAuslov_spajanja(DEKARTOV_PROIZVOD(tabela1, tabela2))
	  	 *
		 */
        Transaction tx = new Transaction();
        Plan p1 = new TablePlan("student", tx);
        Plan p2 = new TablePlan("smer", tx);


        // Kreiranje predikatskog uslova 'smerid=1'
        Expression lhs1 = new FieldNameExpression("smerid");
        Constant c = new IntConstant(1);
        Expression rhs1 = new ConstantExpression(c);
        Term t1 = new Term(lhs1, rhs1);
        Predicate pred1 = new Predicate(t1); //smerid=1  (SI)

        Plan p3 = new SelectionPlan(p1, pred1);

        Plan p4 = new CrossProductPlan(p3, p2);

        // Kreiranje predikatskog uslova 'smerid=smid', sto je uslov spajanja/join-a
        Expression lhs2 = new FieldNameExpression("smerid");
        Expression rhs2 = new FieldNameExpression("smid");
        Term t2 = new Term(lhs2, rhs2);
        Predicate pred2 = new Predicate(t2); //smerid=smid  - Join uslov

        Plan p5 = new SelectionPlan(p4, pred2);


        List<String> fields = Arrays.asList("sname", "smername");
        Plan p6 = new ProjectionPlan(p5, fields);

        Scan s = p6.open();

        System.out.println("\nStudenti sa smera Softversko inzenjerstvo:");
        System.out.println("\nStudent\t\t\tSmer");
        System.out.println("-----------------------------------------");
        while (s.next()) {
            String sname = s.getString("sname"); 	//SimpleDB cuva naziv kolona
            String dname = s.getString("smername"); //sa malim slovima (lower case)
            System.out.println(sname + "\t\t" + dname);
        }
        System.out.println();
        s.close();
        tx.commit();

    }

    private static void queryManualPlan3(){

       /*
            SQL EKVIVALENT:
            SELECT studname, COUNT(ocena) AS desetke
            FROM STUDENT, PREDMET, ISPIT, POLAGANJE
            WHERE ispid=ispitid
            AND pid=predmetid
            AND sid=polagStudId
            AND predGod = 1
            AND ocena=10
            GROUP by sid
            ORDER BY desetke
        */

        Transaction tx = new Transaction();
        Plan studentTable = new TablePlan("student", tx);
        Plan predmetTable = new TablePlan("predmet", tx);
        Plan ispitTable = new TablePlan("ispit", tx);
        Plan polaganjeTable =  new TablePlan("polaganje", tx);


        // Kreiranje predikatskog uslova 'predGod = 1'
        Expression lhs1 = new FieldNameExpression("predgod");
        Constant c = new IntConstant(1);
        Expression rhs1 = new ConstantExpression(c);
        Term t1 = new Term(lhs1, rhs1);
        Predicate predPregGod = new Predicate(t1);


        // Kreiranje predikatskog uslova 'ocena = 10'
        Expression lhs2 = new FieldNameExpression("ocena");
        Constant c2 = new IntConstant(10);
        Expression rhs2 = new ConstantExpression(c2);
        Term t2 = new Term(lhs2, rhs2);
        Predicate polaganjeOcene = new Predicate(t2);


        // Kreiranje predikatskog uslova 'ispid = ispitid', sto je uslov join-a
        Expression lhs3 = new FieldNameExpression("ispid");
        Expression rh3 = new FieldNameExpression("ispitid");
        Term t3 = new Term(lhs3, rh3);
        Predicate predIspit = new Predicate(t3);

        //Kreiranje predikatskog uslova 'pid = predmetid'
        Expression lhs4 = new FieldNameExpression("pid");
        Expression rhs4 = new FieldNameExpression("predmetid");
        Term t4 = new Term(lhs4, rhs4);
        Predicate predmetIspit = new Predicate(t4);

        //Kreiranje predikatskog uslova 'sid = polagStudId'
        Expression lhs5 = new FieldNameExpression("sid");
        Expression rhs5 = new FieldNameExpression("polagstudid");
        Term t5 = new Term(lhs5, rhs5);
        Predicate studentPolaganje = new Predicate(t5);


        Plan p1 = new CrossProductPlan(polaganjeTable, ispitTable);

        Plan p2 = new CrossProductPlan(p1, predmetTable);

        Plan p3 = new CrossProductPlan(p2, studentTable);

        Plan selekcijaPredmeta = new SelectionPlan(p3, predmetIspit);

        Plan selekcijaStudenta = new SelectionPlan(selekcijaPredmeta, studentPolaganje);

        Plan selekcijaIspita = new SelectionPlan(selekcijaStudenta,predIspit);

        Plan selekcijaOcena = new SelectionPlan(selekcijaIspita,polaganjeOcene);

        Plan selekcijaPredmetGodine = new SelectionPlan(selekcijaOcena,predPregGod);



        List<String> fields = Arrays.asList("sid", "studname","predgod","ocena");
        Plan finalPlan = new ProjectionPlan(selekcijaPredmetGodine,fields);

        Scan s = finalPlan.open();
        System.out.println("\n Najbolji studenti:\n");
        System.out.println("Student\t\tStudentIme\t\tPred godina\t\tOcena");
        System.out.println("---------------------------------------------");
        while (s.next()) {
            int sid = s.getInt("sid");
            String studname = s.getString("studname");
            int predgod = s.getInt("predgod");
            int ocena = s.getInt("ocena");

            System.out.printf("%d\t\t\t%s\t\t%d\t\t%d\n",sid,studname,predgod,ocena);
        }
        s.close();
        // Group by, Count(ocena)
        Collection<String> groupFields = Arrays.asList("sid");
        AggregationFn countAggr = new CountFn("ocena");
        Collection<AggregationFn> aggFunctions = Arrays.asList(countAggr);
        Plan finalGroupPlan = new GroupByPlan(finalPlan,groupFields,aggFunctions,tx);

        //Sortiranje
        List<String> sortFields = Arrays.asList("countofocena");
        Plan sortFinalPlan = new SortPlan(finalGroupPlan,sortFields,tx);

        System.out.println("Schema Group By ="+finalGroupPlan.schema().fields());

        System.out.println("Schema Sort By ="+sortFinalPlan.schema().fields());
        Scan scan = sortFinalPlan.open();

        System.out.println("\n Najbolji studenti:\n");
        System.out.println("Student\t\t\tBroj10-ki");
        System.out.println("-------------------------");
        while (scan.next()){
            int sid = scan.getInt("sid");
            int count = scan.getInt("countofocena");
            System.out.printf("%s\t\t%d\n",sid,count);
        }
        scan.close();
        tx.commit();
    }

    private static void queryManualPlan3Optimization(){
        /*
            SQL EKVIVALENT:
            SELECT studname, COUNT(ocena) AS desetke
            FROM STUDENT, PREDMET, ISPIT, POLAGANJE
            WHERE ispid=ispitid
            AND pid=predmetid
            AND sid=polagStudId
            AND predGod = 1
            AND ocena=10
            GROUP by sid
            ORDER BY desetke
        */

        Transaction tx = new Transaction();
        Plan studentTable = new TablePlan("student", tx);
        Plan predmetTable = new TablePlan("predmet", tx);
        Plan ispitTable = new TablePlan("ispit", tx);
        Plan polaganjeTable =  new TablePlan("polaganje", tx);

        // Kreiranje predikatskog uslova 'predGod = 1'
        Expression lhs1 = new FieldNameExpression("predgod");
        Constant c = new IntConstant(1);
        Expression rhs1 = new ConstantExpression(c);
        Term t1 = new Term(lhs1, rhs1);
        Predicate predPregGod = new Predicate(t1);

        Plan predmetFilter = new SelectionPlan(predmetTable,predPregGod);

        // Kreiranje predikatskog uslova 'ocena = 10'
        Expression lhs2 = new FieldNameExpression("ocena");
        Constant c2 = new IntConstant(10);
        Expression rhs2 = new ConstantExpression(c2);
        Term t2 = new Term(lhs2, rhs2);
        Predicate predOcene = new Predicate(t2);

        Plan oceneFilter = new SelectionPlan(polaganjeTable,predOcene);

        // Kreiranje predikatskog uslova 'ispid = ispitid', sto je uslov join-a
        Expression lhs3 = new FieldNameExpression("ispid");
        Expression rh3 = new FieldNameExpression("ispitid");
        Term t3 = new Term(lhs3, rh3);
        Predicate predJoin = new Predicate(t3);

        //Kreiranje predikatskog uslova 'pid = predmetid'
        Expression lhs4 = new FieldNameExpression("pid");
        Expression rhs4 = new FieldNameExpression("predmetid");
        Term t4 = new Term(lhs4, rhs4);
        Predicate predmetIspit = new Predicate(t4);

        //Kreiranje predikatskog uslova 'sid = polagStudId'
        Expression lhs5 = new FieldNameExpression("sid");
        Expression rhs5 = new FieldNameExpression("polagstudid");
        Term t5 = new Term(lhs5, rhs5);
        Predicate studentPolaganje = new Predicate(t5);

        Plan p1 = new CrossProductPlan(predmetFilter, ispitTable);
        Plan joinPredmetIspit = new SelectionPlan(p1, predmetIspit); // pid=predmetid

        Plan p2 = new CrossProductPlan(joinPredmetIspit, oceneFilter);
        Plan joinIspitPolaganje = new SelectionPlan(p2, predJoin); // ispid=ispitid

        Plan p3 = new CrossProductPlan(joinIspitPolaganje, studentTable);
        Plan fullJoinPlan = new SelectionPlan(p3, studentPolaganje); // sid=polagStudId

        List<String> fields = Arrays.asList("sid", "studname","predgod","ocena");
        Plan finalPlan = new ProjectionPlan(fullJoinPlan,fields);

        Scan s = finalPlan.open();
        System.out.println("\n Najbolji studenti:\n");
        System.out.println("Student\t\tStudentIme\t\tPred godina\t\tOcena");
        System.out.println("---------------------------------------------");
        while (s.next()) {
            int sid = s.getInt("sid");
            String studname = s.getString("studname");
            int predgod = s.getInt("predgod");
            int ocena = s.getInt("ocena");

            System.out.printf("%d\t\t\t%s\t\t%d\t\t%d\n",sid,studname,predgod,ocena);
        }
        s.close();
        // Group by, Count(ocena)
        Collection<String> groupFields = Arrays.asList("sid");
        AggregationFn countAggr = new CountFn("ocena");
        Collection<AggregationFn> aggFunctions = Arrays.asList(countAggr);
        Plan finalGroupPlan = new GroupByPlan(finalPlan,groupFields,aggFunctions,tx);

        //Sortiranje
        List<String> sortFields = Arrays.asList("countofocena");
        Plan sortFinalPlan = new SortPlan(finalGroupPlan,sortFields,tx);

        System.out.println("Schema Group By ="+finalGroupPlan.schema().fields());
        //GroupByScan scan = (GroupByScan)finalGroupPlan.open();

        System.out.println("Schema Sort By ="+sortFinalPlan.schema().fields());
        Scan scan = sortFinalPlan.open();

        System.out.println("\n Najbolji studenti:\n");
        System.out.println("Student\t\t\tBroj10-ki");
        System.out.println("-------------------------");
        while (scan.next()){
            int sid = scan.getInt("sid");
            int count = scan.getInt("countofocena");
            System.out.printf("%s\t\t%d\n",sid,count);
        }
        scan.close();
        tx.commit();

    }

    private static void querySQL1() {
        // Kreiranje transakcije
        Transaction tx = new Transaction();

        // Kreiranje SQL upita
        String sqlQuery = "select sname, smerName "
                + "from STUDENT, SMER "
                + "where smerId = SMId";

        // Parsiranje upita i kreiranje plana izvrsavanja upita
        //Plan p = SimpleDBEngine.planner().createQueryPlan(qry, tx);
        QueryData queryResult =  SimpleDBEngine.planner().getParsingResult(sqlQuery);
        System.out.println("Rezultat Parsiranja:\n"+queryResult);
        Plan p = SimpleDBEngine.planner().createQueryPlanFromParsingResult(queryResult, tx);

        System.out.println("\nLOGICKI PLAN UPITA:");
        p.printPlan(0);

        // Inicijalizacija plana upita, tj. svih operatora u stablu upita koji se izvrsavaju
        Scan s = p.open();

        System.out.println("\nSpisak svih studenata:\n");
        System.out.println("Student\t\t\tSmer");
        System.out.println("-----------------------------------------");
        while (s.next()) {
            String sname = s.getString("sname"); 	//SimpleDB cuva naziv kolona
            String dname = s.getString("desetke"); //sa malim slovima (lower case)
            System.out.println(sname + "\t\t" + dname);
        }
        s.close();
        tx.commit();
    }




    /**
     * Executes the specified SQL query string.
     * The method calls the query planner to create a plan
     * for the query.
     */
    public static void executeSQLQuery(String sqlQuery) {
        Transaction tx = null;
        try {
            tx = new Transaction();
            Plan plan = SimpleDBEngine.planner().createQueryPlan(sqlQuery, tx);
            Scan scan = plan.open();
            Schema sch = plan.schema();

            while (scan.next()) {
                //String sname = scan.getString("sname");
                //int age = scan.getInt("age");


            }

        }
        catch(RuntimeException e) {
            tx.rollback();
            throw e;
        }
    }

    /**
     * Executes the specified SQL update command.
     * The method sends the command to the update planner,
     * which executes it.
     */
    public static int executeSQLUpdate(String sqlCmd) {
        Transaction tx = null;
        try {
            tx = new Transaction();
            int result = SimpleDBEngine.planner().executeUpdate(sqlCmd, tx);

            tx.commit();
            return result;
        }
        catch(RuntimeException e) {
            tx.rollback();
            throw e;
        }
    }

    public static boolean initStudentDB() {
        // Kreiranje baze podataka, sto podrazumeva fajl sa podacima i podatke u sistemskom katalogu
        return SimpleDBEngine.init("studentdb");
    }

    public static void initStudentDBData() {
        String createTableSQL = "create table STUDENT(sid int, sname varchar(16), smerId int, godStud int, godUpis int, godDipl int)";

        MainQueryRunner.executeSQLUpdate(createTableSQL);
        System.out.println("Table STUDENT created.");

        String insertSQLString = "insert into STUDENT(sid, sname, smerId, godStud, godUpis, godDipl) values ";
        String[] studvals = {"(1, 'Milan Petrovic', 1, 4, 2021, 2025)",
                "(2, 'Jovan Jovanovic', 2, 3, 2019, 2025)",
                "(3, 'Ana Mirkovic', 3, 1, 2023, 2021)",
                "(4, 'Maja Spasic', 2, 2, 2018, 2023)",
                "(5, 'Veljko Peric', 3, 3, 2020, 2022)",
                "(6, 'Bojana Mijatovic', 1, 4, 2024, 2025)",
                "(7, 'Lazar Kostic', 2, 1, 2019, 2025)",
                "(8, 'Milica Milic', 3, 3, 2021, 2024)"};
        for (int i=0; i<studvals.length; i++)
            MainQueryRunner.executeSQLUpdate(insertSQLString + studvals[i]);
        System.out.println("STUDENT records inserted.");

        createTableSQL = "create table SMER(smid int, smerName varchar(25))";
        MainQueryRunner.executeSQLUpdate(createTableSQL);
        System.out.println("Table SMER created.");

        insertSQLString = "insert into SMER(smid, smerName) values ";
        String[] deptvals = {"(1, 'Softversko inzenjerstvo')",
                "(2, 'Racunarsko inzenjerstvo')",
                "(3, 'Racunarske nauke')"};
        for (int i=0; i<deptvals.length; i++)
            MainQueryRunner.executeSQLUpdate(insertSQLString + deptvals[i]);
        System.out.println("SMER records inserted.");

        createTableSQL = "create table PREDMET(PId int, naziv varchar(25), smerId int, predGod int)";
        MainQueryRunner.executeSQLUpdate(createTableSQL);
        System.out.println("Table PREDMET created.");

        insertSQLString = "insert into PREDMET(PId, naziv, smerId) values ";
        String[] coursevals = {"(12, 'Napredne Baze Podataka', 1, 3)",
                "(22, 'Distribuirani Sistemi', 2, 4)",
                "(32, 'Teorija Algoritama', 3, 4)",
                "(42, 'Diskretna Matematika', 3, 1)",
                "(52, 'Racunarske Mreze', 2, 2)",
                "(62, 'Softverske metodologije', 1, 3)"};
        for (int i=0; i<coursevals.length; i++)
            MainQueryRunner.executeSQLUpdate(insertSQLString + coursevals[i]);
        System.out.println("PREDMET records inserted.");

        // --- 1. Kreiranje i punjenje tabele ISPITNIROK ---
        String createRokSQL = "create table ISPITNIROK(rokId int, ispRokNaziv varchar(25))";
        MainQueryRunner.executeSQLUpdate(createRokSQL);
        System.out.println("Table ISPITNIROK created.");

        String insertRokSQL = "insert into ISPITNIROK(rokId, ispRokNaziv) values ";
        String[] rokVals = {
                "(1, 'Januarski rok')",
                "(2, 'Aprilski rok')",
                "(3, 'Junski rok')",
                "(4, 'Septembarski rok')"
        };
        for (String val : rokVals)
            MainQueryRunner.executeSQLUpdate(insertRokSQL + val);
        System.out.println("ISPITNIROK records inserted.");


        // --- 2. Kreiranje i punjenje tabele ISPIT ---
        // Napomena: predmetid mora postojati u tabeli PREDMET (koristimo 12, 32, 62...)
        String createIspitSQL = "create table ISPIT(ispid int, predmetid int, ispitniRokId int, ispUGod int, ispDatum varchar(25))";
        MainQueryRunner.executeSQLUpdate(createIspitSQL);
        System.out.println("Table ISPIT created.");

        String insertIspitSQL = "insert into ISPIT(ispid, predmetid, ispitniRokId, ispUGod, ispDatum) values ";
        String[] ispitVals = {
                "(101, 12, 1, 2024, '15.01.2024')", // Napredne baze (12) u Januarskom (1)
                "(102, 32, 1, 2024, '20.01.2024')", // Teorija Alg. (32) u Januarskom (1)
                "(103, 12, 3, 2024, '10.06.2024')", // Napredne baze (12) u Junskom (3)
                "(104, 62, 3, 2024, '15.06.2024')", // Soft. Metod. (62) u Junskom (3)
                "(105, 52, 4, 2024, '01.09.2024')",  // Rac. Mreze (52) u Septembru (4)
                "(106, 42, 3, 2024, '15.06.2024')"
        };
        for (String val : ispitVals)
            MainQueryRunner.executeSQLUpdate(insertIspitSQL + val);
        System.out.println("ISPIT records inserted.");


        // --- 3. Kreiranje i punjenje tabele POLAGANJE ---
        // Napomena: polagStudId su studenti (1-9), ispitId su ispiti iznad (101-105)
        String createPolaganjeSQL = "create table POLAGANJE(polagStudId int, ispitId int, ocena int)";
        MainQueryRunner.executeSQLUpdate(createPolaganjeSQL);
        System.out.println("Table POLAGANJE created.");

        String insertPolaganjeSQL = "insert into POLAGANJE(polagStudId, ispitId, ocena) values ";
        String[] polaganjeVals = {
                "(1, 101, 10)", // Milan (1) dobio 10 iz Naprednih baza
                "(2, 101, 8)",  // Jovan (2) dobio 8 iz Naprednih baza
                "(3, 102, 9)",  // Ana (3) dobila 9 iz Teorije Alg.
                "(1, 102, 10)", // Milan (1) dobio 10 iz Teorije Alg.
                "(5, 103, 7)",  // Veljko (5) dobio 7 iz Naprednih baza (Jun)
                "(6, 104, 10)", // Bojana (6) dobila 10 iz Soft. Metod.
                "(2, 105, 6)",  // Jovan (2) dobio 6 iz Mreza
                "(8, 101, 5)",// Milica (8) pala Napredne baze
                "(2, 106, 10)"
        };
        for (String val : polaganjeVals)
            MainQueryRunner.executeSQLUpdate(insertPolaganjeSQL + val);
        System.out.println("POLAGANJE records inserted.");
			/*
			s = "create table SECTION(SectId int, CourseId int, Prof varchar(8), YearOffered int)";
			stmt.executeUpdate(s);
			System.out.println("Table SECTION created.");

			s = "insert into SECTION(SectId, CourseId, Prof, YearOffered) values ";
			String[] sectvals = {"(13, 12, 'turing', 2004)",
								 "(23, 12, 'turing', 2005)",
								 "(33, 32, 'newton', 2000)",
								 "(43, 32, 'einstein', 2001)",
								 "(53, 62, 'brando', 2001)"};
			for (int i=0; i<sectvals.length; i++)
				stmt.executeUpdate(s + sectvals[i]);
			System.out.println("SECTION records inserted.");

			s = "create table ENROLL(EId int, StudentId int, SectionId int, Grade varchar(2))";
			stmt.executeUpdate(s);
			System.out.println("Table ENROLL created.");

			s = "insert into ENROLL(EId, StudentId, SectionId, Grade) values ";
			String[] enrollvals = {"(14, 1, 13, 'A')",
								   "(24, 1, 43, 'C' )",
								   "(34, 2, 43, 'B+')",
								   "(44, 4, 33, 'B' )",
								   "(54, 4, 53, 'A' )",
								   "(64, 6, 53, 'A' )"};
			for (int i=0; i<enrollvals.length; i++)
				stmt.executeUpdate(s + enrollvals[i]);
			System.out.println("ENROLL records inserted.");
			*/
    }

}