CREATE TABLE IF NOT EXISTS titles (
  id TEXT PRIMARY KEY, title TEXT NOT NULL, type TEXT, year INTEGER, rating REAL, imdb REAL, runtime INTEGER, seasons INTEGER,
  overview TEXT, genres TEXT, cast_list TEXT, options TEXT, langs TEXT, poster TEXT, backdrop TEXT, logo TEXT, trailer TEXT,
  pop INTEGER DEFAULT 999999, added_at INTEGER, leaving_at INTEGER, orig TEXT, blurb TEXT, world INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS signals ( id TEXT, kind TEXT, at INTEGER, PRIMARY KEY (id, kind) );
CREATE TABLE IF NOT EXISTS profile ( key TEXT PRIMARY KEY, value TEXT );
CREATE TABLE IF NOT EXISTS asks ( text TEXT, mood TEXT, meant TEXT, shown TEXT, at INTEGER );

-- Without these, every lookup scans every title and the free D1 read limit runs out.
CREATE INDEX IF NOT EXISTS titles_name ON titles(title COLLATE NOCASE, pop);
CREATE INDEX IF NOT EXISTS titles_pop ON titles(pop);
CREATE INDEX IF NOT EXISTS titles_added ON titles(added_at);
CREATE INDEX IF NOT EXISTS titles_leaving ON titles(leaving_at);
CREATE INDEX IF NOT EXISTS titles_world ON titles(world, imdb);
CREATE INDEX IF NOT EXISTS titles_gems ON titles(imdb) WHERE blurb IS NOT NULL;
CREATE INDEX IF NOT EXISTS titles_noblurb ON titles(pop) WHERE blurb IS NULL;
CREATE INDEX IF NOT EXISTS asks_at ON asks(at);
