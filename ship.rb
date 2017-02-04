#!/usr/bin/env ruby

$perform = false

if ARGV.count > 0 && ARGV[0] == 'perform'
  $perform = true
end

def sh(cmd)
  if $perform
    puts "> #{cmd}"
    `#{cmd}`
  else
    puts "# #{cmd}"
  end
end

version = File.read('version.txt').strip

if version !~ /SNAPSHOT$/
  raise "Not on snapshot version!"
end

version.gsub!(/-SNAPSHOT$/, '')

puts "Shipping #{version}..."

parts = version.split('.')
parts[-1] = (parts.last.to_i + 1).to_s
next_version = parts.join('.')

puts "Next version? [#{next_version}]"
input = $stdin.gets.strip
if input.length > 0
  next_version = input
end

next_version = next_version + "-SNAPSHOT"

if $perform
  puts "Performing release:"
else
  puts "I would run the following shell commands:"
end

# update version.txt
sh("echo '#{version}' > version.txt")
# commit ship version
sh("git add version.txt")
sh("git commit -m 'Release version #{version}'")
# tag
sh("git tag -a 'release-#{version}' -m 'Tag release-#{version}'")
# bump to new snapshot
sh("echo '#{next_version}' > version.txt")
sh("git add version.txt")
sh("git commit -m 'Bump development version to #{next_version}'")
# push
sh("git push --tags")
# ship
sh("git checkout 'release-#{version}'")
sh("./gradlew clean jar generatePomFileForPluginMavenPublication bintrayUpload")
sh("git checkout master")
