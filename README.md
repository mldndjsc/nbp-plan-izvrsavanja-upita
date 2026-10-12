# Query Execution Plans in SimpleDB

![Java](https://img.shields.io/badge/Java-17-orange)
![Maven](https://img.shields.io/badge/Maven-build-C71A36)
![Topic](https://img.shields.io/badge/topic-query%20optimization-blue)

Hand-built and optimized **relational query execution plans** on top of *SimpleDB*, a small educational relational DBMS written in Java. The project shows how an SQL query becomes a tree of relational-algebra operators. It also shows how reordering that tree (selection pushdown and join ordering) changes the amount of work by orders of magnitude.

Built for the *Advanced Databases* course at the School of Computing (RAF), Belgrade.

> SimpleDB was created by Edward Sciore for the book *Database Design and Implementation*. This repository uses the course's adapted version (`raf-simpledb`).

---

## The query

Using a student database, find **how many 10s (top grades) each student got in first-year courses**, ordered by that count:

```sql
SELECT studname, COUNT(ocena) AS desetke
FROM   STUDENT, PREDMET, ISPIT, POLAGANJE
WHERE  ispid = ispitid
  AND  pid   = predmetid
  AND  sid   = polagStudId
  AND  predGod = 1
  AND  ocena   = 10
GROUP BY sid
ORDER BY desetke
```

The plan is assembled manually from SimpleDB operators: `TablePlan`, `SelectionPlan`, `ProjectionPlan`, `CrossProductPlan`, `GroupByPlan` with `CountFn`, and `SortPlan`.

---

## Naive plan vs. optimized plan

**Naive plan.** Take the cross product of all four tables first, then apply every condition on top:

```
π sid, studname, predgod, ocena
└─ σ predGod=1
   └─ σ ocena=10
      └─ σ ispid=ispitid
         └─ σ sid=polagStudId
            └─ σ pid=predmetid
               └─ POLAGANJE × ISPIT × PREDMET × STUDENT
```

**Optimized plan.** Push selections down to the base tables and pick a join order in which every cross product is followed immediately by its join condition:

```
π sid, studname, predgod, ocena
└─ σ sid=polagStudId
   └─ ( σ ispid=ispitid
        └─ ( σ pid=predmetid
             └─ ( σ predGod=1 (PREDMET) × ISPIT ) )
           × σ ocena=10 (POLAGANJE) )
      × STUDENT
```

Both plans then go through `GROUP BY sid` with `COUNT(ocena)`, followed by a sort.

### Why it matters

With the included dataset (150 students, 167 courses, 2,004 exams, 5,656 exam attempts), the tuple combinations produced by the cross products are:

| Plan | Tuple combinations |
|---|---|
| Naive (one big cross product) | **≈ 2.8 × 10¹¹** |
| Optimized (filter early, join step by step) | **≈ 2.2 × 10⁵** |

That is roughly **a million times less work** for the same result: 77 students. Filtering first shrinks the inputs (30 first-year courses instead of 167, and 392 tens instead of 5,656 attempts), and joining step by step keeps every intermediate result small.

---

## What else is in the engine

The course version of SimpleDB includes the full stack of a small DBMS, which the project uses to load and query data:

- **Storage:** file, log and buffer managers; record pages and record files
- **Catalog:** table, view, index and statistics metadata
- **Query processing:** lexer and parser, a basic and a heuristic query planner, an update planner
- **Operators:** selection, projection, product, multi-buffer product, index select, index join, sort, group-by
- **Indexes:** hash index
- **Transactions**

The dataset (students, study programs, courses, exam periods, exams and exam attempts) is loaded from CSV files in `data/` by a generic CSV-to-`INSERT` loader in `InitKolokvijumDB`.

---

## Running

**Requirements:** JDK 17, Maven, and an IDE such as IntelliJ IDEA or Eclipse.

1. Open the project as a Maven project.
2. Run `rs.raf.simpledb.MainQueryRunner` with the project root as the working directory, because the CSV files are read from `data/`.

On the first run, the database directory `studentdb` is created and the tables are filled from the CSV files. In `main`, choose which query method to run, for example `queryManualPlan3()` for the naive plan or `queryManualPlan3Optimization()` for the optimized one.

---

## Project structure

```
src/main/java/rs/raf/simpledb/
├── MainQueryRunner.java       # manually built query plans (naive & optimized)
├── InitKolokvijumDB.java      # schema creation + CSV data loading
├── SimpleDBEngine.java        # engine bootstrap
├── planner/                   # query & update planners
├── query/                     # plans, predicates, expressions, aggregation
├── query/operators/           # scans (table, select, project, product, index…)
├── operators/                 # sort, chunk & multi-buffer product scans
├── index/                     # hash index
├── metadata/                  # catalog managers
├── record/                    # record pages & schemas
├── tx/                        # transactions
└── utils/                     # file, log & buffer managers
data/                          # CSV dataset
```
