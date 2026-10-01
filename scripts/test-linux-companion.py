# Added for Resume Terminal, 2026-09-30 to 2026-10-01.
# SPDX-License-Identifier: GPL-3.0-or-later
import os, pathlib, tempfile, subprocess, select, time, signal, json, shutil, errno
repo=pathlib.Path(__file__).resolve().parent.parent
home=pathlib.Path(tempfile.mkdtemp(prefix='resume-real-linux-'))
env=os.environ.copy(); env['HOME']=str(home); env['PATH']='/usr/bin:/bin'; env['ZMX_DIR']=str(home/'.local/state/resume-terminal/zmx'); env['ZMX_NO_DETACH_KEY']='1'; env.pop('ZMX_SESSION_PREFIX',None)
report={'environment':'isolated home on WSL2 Ubuntu 22.04 x86_64','home':str(home)}
children=[]
def run(*args):
 p=subprocess.run(args,env=env,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=115)
 assert p.returncode==0,p.stdout
 return p.stdout
def attach():
 import pty
 pid,fd=pty.fork()
 if pid==0:
  os.chdir(str(home)); os.execve('/bin/sh',['sh',str(home/'.local/bin/remote-work'),'installer-check'],env)
 children.append(pid)
 os.set_blocking(fd,False)
 return pid,fd
def read_until(fd,marker,limit=10):
 data=b'';deadline=time.monotonic()+limit
 while time.monotonic()<deadline:
  if select.select([fd],[],[],.2)[0]:
   try:data+=os.read(fd,65536)
   except (OSError,BlockingIOError):break
   if marker in data:return data
 raise AssertionError(repr(data[-2000:]))
try:
 report['initial_install_output']=run('sh',str(repo/'wsl/install.sh'))
 zmx=str(home/'.local/share/resume-terminal/bin/zmx')
 assert pathlib.Path(zmx).is_file()
 report['version']=run(zmx,'version').strip()
 pid,fd=attach();time.sleep(.5)
 os.write(fd,b'RESUME_PROBE=kept; export RESUME_PROBE; printf "PROBE_STARTED\\n"\r')
 read_until(fd,b'PROBE_STARTED');time.sleep(.3);os.close(fd)
 time.sleep(.5)
 listing=run(zmx,'list');assert 'name=installer-check' in listing,listing
 before=listing.split('pid=')[1].split('\t')[0]
 report['repeat_install_output']=run('sh',str(repo/'wsl/install.sh'))
 after=run(zmx,'list').split('pid=')[1].split('\t')[0]
 assert before==after,(before,after)
 report['same_session_pid_after_reinstall']=True
 pid,fd=attach();time.sleep(.5)
 os.write(fd,b'printf "VALUE_%s_END\\n" "$RESUME_PROBE"\r')
 output=read_until(fd,b'VALUE_kept_END');report['state_survives_disconnect_and_reattach']=True
 os.close(fd)
 report['state_directory_mode']=oct((home/'.local/state/resume-terminal/zmx').stat().st_mode&0o777)
 print(json.dumps(report,ensure_ascii=False,indent=2))
finally:
 zmx=home/'.local/share/resume-terminal/bin/zmx'
 if zmx.exists():subprocess.run([str(zmx),'kill','installer-check'],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 for pid in children:
  try:os.kill(pid,signal.SIGTERM)
  except ProcessLookupError:pass
  try:os.waitpid(pid,0)
  except ChildProcessError:pass
 # zmx can finish flushing its log just after its kill acknowledgement.
 for attempt in range(40):
  try:
   shutil.rmtree(home)
   break
  except FileNotFoundError:break
  except OSError as error:
   if error.errno != errno.ENOTEMPTY or attempt == 39:raise
   time.sleep(.05)
