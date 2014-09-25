JAVASOURCES = $(wildcard *.java)
CSOURCES = $(wildcard *.c *.cc bigint/B*.cc)
CHEADERS = $(wildcard *.h)

ifeq ($(shell uname),Darwin)

RELEASE = requiem-4.1-mac

MAC_JAVASOURCES = $(filter-out WindowsConfig.java, $(JAVASOURCES))
$(RELEASE).zip: $(JAVASOURCES) $(CSOURCES) $(CHEADERS)
	javac -target 1.5 $(MAC_JAVASOURCES)
	g++ -m32 -mmacosx-version-min=10.5 -O3 -I/System/Library/Frameworks/JavaVM.framework/Headers -dynamiclib -framework ApplicationServices -o libNative32.dylib MacConfig.cc
	g++ -m64 -mmacosx-version-min=10.5 -O3 -I/System/Library/Frameworks/JavaVM.framework/Headers -dynamiclib -framework ApplicationServices -o libNative64.dylib MacConfig.cc
	g++ -DWINDOWS=0 -m32 -mmacosx-version-min=10.5 -O3 decrypt_track.cc sha1.c aes.c $(wildcard bigint/B*.cc) -o decrypt_track
	jar cf Requiem.jar *.class libNative32.dylib libNative64.dylib decrypt_track CoreFP-2.1.34/CoreFP.i386 CoreFP-2.1.34/CoreFP.icxs CoreFP1-1.14.34/CoreFP1.i386 CoreFP1-1.14.34/CoreFP1.icxs
	ant -f macbuild.xml bundle
	rm -fr $(RELEASE) $(RELEASE).zip
	mkdir $(RELEASE)
	cp README $(RELEASE)
	cp -r Requiem.app $(RELEASE)
	zip -r $(RELEASE).zip $(RELEASE)

else

RELEASE = requiem-4.1-win

WIN_JAVASOURCES = $(filter-out MacConfig.java, $(JAVASOURCES))
$(RELEASE).zip: $(JAVASOURCES) $(CSOURCES) $(CHEADERS)
	javac -target 1.5 $(WIN_JAVASOURCES)
	g++ -m32 -O3 -mno-cygwin "-I$(JAVA_HOME)/include" "-I$(JAVA_HOME)/include/win32" -Wl,--add-stdcall-alias -shared -o Native32.dll WindowsConfig.cc md5.c
	x86_64-w64-mingw32-g++ -m64 -O3 -mno-cygwin "-I$(JAVA_HOME)/include" "-I$(JAVA_HOME)/include/win32" -Wl,--add-stdcall-alias -shared -static-libgcc -static-libstdc++ -o Native64.dll WindowsConfig.cc md5.c
	g++ -DWINDOWS=1 -m32 -mno-cygwin -O3 decrypt_track.cc sha1.c aes.c $(wildcard bigint/B*.cc) -o decrypt_track
	jar cf Requiem.jar *.class Native32.dll Native64.dll decrypt_track CoreFPWin-2.2.19/CoreFP.dll
	ant -f winbuild.xml -v bundle
	rm -fr $(RELEASE) $(RELEASE).zip
	mkdir $(RELEASE)
	cp README $(RELEASE)
	cp -r Requiem.exe $(RELEASE)
	zip -r $(RELEASE).zip $(RELEASE)

endif

SRCRELEASE = requiem-4.1-src
$(SRCRELEASE).zip: $(JAVASOURCES) $(CSOURCES) $(CHEADERS)
	rm -fr $(SRCRELEASE) $(SRCRELEASE).zip
	mkdir $(SRCRELEASE)
	cp -r *.java *.cc *.c *.h bigint CoreFP-2.1.34 CoreFP1-1.14.34 CoreFPWin-2.2.19 Makefile README macbuild.xml winbuild.xml requiem.icns requiem.ico $(SRCRELEASE)
	rm -fr $(SRCRELEASE)/*/CVS
	zip -r $(SRCRELEASE).zip $(SRCRELEASE)

all:	$(RELEASE).zip $(SRCRELEASE).zip

clean:
	rm -fr *~ *.class libNative32.dylib libNative64.dylib Native32.dll Native64.dll decrypt_track Requiem.jar Requiem.app $(RELEASE) $(RELEASE).zip $(SRCRELEASE) $(SRCRELEASE).zip
