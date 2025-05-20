# 1. Remove the existing Git metadata
rm -rf .git

# 2. Re-init, add everything, and make a fresh initial commit
git init
git add .
git commit -m "Initial import of ChatAI MCServerPlugin"

# 3. (Re-)add your GitHub remote
git remote add origin https://github.com/QuaGMiRe001/ChatAI_MCServerPlugin.git

# 4. Push up as the new main branch, overwriting whatever’s there
git push -u --force origin main
