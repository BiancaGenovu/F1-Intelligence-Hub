import pandas as pd
df = pd.read_excel('F1_2025Grid.xlsx')
print(df.columns.tolist())
print(df[['Abbreviation', 'CircuitName', 'Year']].head(10))