# GO Cook — KNN model evaluation

Status: **OK_SYNTHETIC_OR_REAL_UNVERIFIED**

Catalog: 184 menus | Interactions: 805 | Eligible users: 134

Holdout: last distinct menu per user; earlier distinct menus are the profile.
All scores are macro-averages. Popularity is trained only on earlier events.

| Model | Precision@5 | Recall@5 | HitRate@5 | NDCG@5 |
|---|---:|---:|---:|---:|
| Item-KNN | 0.039 | 0.194 | 0.194 | 0.109 |
| Popularity | 0.001 | 0.007 | 0.007 | 0.003 |

**Limitation:** This is an offline historical-item test, not a calibrated likelihood of customer preference.
Synthetic samples must be labeled DEMO, never presented as production evaluation.
