# Sample / Seed Data

This project does not ship a static SQL data dump. Instead, `scripts/seed.sh` runs
`com.cinemareserve.cli.SeedData`, which creates development data **through the same
service layer the CLI menus use** -- so the seed data exercises the real validation
and business rules (admin-only checks, scheduling-conflict checks, etc.) rather than
being inserted around them.

Running it creates:

| Type         | Details                                                  |
| ------------ | -------------------------------------------------------- |
| Admin user   | username `admin`, password `Admin@123`                   |
| Regular user | username `alice`, password `Password1`                   |
| Genres       | Action, Drama, Comedy, Sci-Fi                            |
| Movie        | "Skyline Protocol" (Action, 128 min, English)            |
| Theatre      | "Galaxy Cineplex", MG Road, Indore                       |
| Screen       | "Screen 1", capacity 40                                  |
| Seats        | a 4-row x 10-seat grid (A1-A10 ... D1-D10), all STANDARD |
| Showtime     | tomorrow at 18:00 for 143 minutes, base price 220.00     |

No real person's personal information is used anywhere in this seed data. The admin
password above is a documented **development-only** credential -- see the Security
Considerations section of the README for why this is safe to publish in a public
repository (only its BCrypt hash is ever stored).

The script is safe to re-run: it checks whether the `admin` user already exists and
skips seeding entirely if so, rather than creating duplicate rows.

To reset and reseed from scratch:

```bash
./scripts/reset-db.sh
./scripts/build.sh
./scripts/seed.sh
```
