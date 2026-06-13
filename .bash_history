pkg install sqlite -y
sqlite3 products.db
nano convert_data.py
python convert_data
python convert_data.py
cd ~/storage/downloads
mv ~/convert_data.py ./
python convert_data.py
nano convert_real_excel.py
python convert_real_excel.py
sqlite-utils rows products.db products --limit 5
sqlite-utils insert products.db products Price_Tag_Master_CTN.csv --csv --encoding=cp1252
sqlite-utils insert products.db products Price_Tag_Master_CTN.csv --csv --encoding=latin-1
sqlite-utils rows products.db products --limit 5
pip install pandas openpyxl
cd
clear
termux-setup-storage
cd ~/storage/shared/Download
ls
cp Near-Expiry-updated.zip ~
cd ~
unzip Near-Expiry-updated.zip ~
cd ~/storage/shared/Download
cp Near-Expiry-updated.zip
clear
cd
cd ~/storage/shared/Download
cp project.zip ~
cd ~
unzip project.zip
cd Near-exp
cd project
ls -la
rm -rf app Near-Expiry build.gradle.kts gradle.properties settings.gradle.kts project.zip
unzip Near-Expiry-updated.zip
git init
git config --global user.name "acbikram"
git config --global user.email "abikram38@gmail.com"
git add .
git commit -m "Initial commit of Near-Expiry updated project"
git branch -M main
git remote add origin https://github.com/acbikram/Near-exp.git
git push -u origin main
git push -u origin main --force
# Remove the old project files (this leaves your hidden .git settings untouched)
rm -rf app build.gradle.kts settings.gradle.kts gradle.properties
# Copy the new zip from your phone's download folder into Termux
cp ~/storage/shared/Download/Near-Expiry-updated.zip ~
# Unzip the new files
unzip Near-Expiry-updated.zip
# 1. Stage all the updated files
git add .
# 2. Commit the changes (you can change the text inside quotes to whatever you want)
git commit -m "Merged latest updates from Claude"
# 3. Push the updates to GitHub
git push origin main
# 1. Clear out the previous version files
rm -rf app build.gradle.kts settings.gradle.kts gradle.properties
# 2. Copy the new zip from your phone's download folder
cp ~/storage/shared/Download/Near-Expiry-updated-1.zip ~
# 3. Unzip the new files
unzip Near-Expiry-updated-1.zip
# 1. Stage all the new changes
git add .
# 2. Commit the changes with a new message
git commit -m "Applied updates from Near-Expiry-updated-1"
# 3. Push to GitHub
git push origin main
exit
