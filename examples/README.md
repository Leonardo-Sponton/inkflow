# Examples

Every file in this folder was exported from Inkflow on a Galaxy Tab S9 FE+. The source is the
*Example · Library ER model* project.

| File | What it is |
|---|---|
| [`library.inkflow`](library.inkflow) | The whole project, with its *Conceptual* (Chen) and *Tables* (Crow's Foot) tabs. |
| [`library-postgresql.sql`](library-postgresql.sql) | DDL for PostgreSQL, exported from the *Tables* tab. |
| [`library-mysql.sql`](library-mysql.sql) | The same schema for MySQL (`AUTO_INCREMENT`, backtick quoting). |
| [`library-sqlite.sql`](library-sqlite.sql) | The same schema for SQLite (`PRAGMA foreign_keys = ON`). |
| [`library.mmd`](library.mmd) | A Mermaid `erDiagram` of the tables. |

## The model

- An **author** writes many books, and a book can have several authors (M:N). The `book_authors`
  table has a composite primary key made of two foreign keys, and these relationships are
  identifying, so they are drawn solid.
- A **member** borrows books. Each borrowing is a row in `loans`, with `loaned_at` (defaulting to
  the current time), `due_date` and an optional `returned_at`. An index on `loans.member_id`
  speeds up "what does this member have?" queries.
- `books.isbn` and `members.email` are unique.

## Open it in the app

1. Copy `library.inkflow` to the device.
2. In Inkflow, tap the **import** icon at the top of the project gallery and pick the file.

The same project is also created by the debug seeder:
`adb shell am start -n io.github.leonardosponton.inkflow/.MainActivity --es seed library`.

## Try the SQL

```sh
sqlite3 library.db < library-sqlite.sql
psql -d mydb -f library-postgresql.sql
mysql mydb < library-mysql.sql
```

The PostgreSQL file was checked on PostgreSQL 18, and the SQLite file was checked with SQLite
including foreign-key enforcement.
