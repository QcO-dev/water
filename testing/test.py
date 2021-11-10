import argparse
import glob
import os
import sys
import subprocess
import configparser
import re

def clean_classes(directory):
	for class_file in glob.glob(os.path.join(directory, "*.class")):
		print(f"Cleaning class file {class_file}")
		os.remove(class_file)

def clean_tests(directory):
	for txt_file in glob.glob(os.path.join(directory, "*.txt")):
		print(f"Cleaning test file {txt_file}")
		os.remove(txt_file)

def main():
	parser = argparse.ArgumentParser()
	parser.add_argument("directory", help="The directory containing test files")
	parser.add_argument("-o", "--output-record", help="Record the outputs of files into .txt files", action="store_true")
	parser.add_argument("-s", "--separate-output", help="Outputs stdout and stderr separately on failure", action="store_true")
	parser.add_argument("-c", "--clean", help="Remove .class files from test folder", action="store_true")
	parser.add_argument("-ct", "--clean-tests", help="Remove .txt files from test folder", action="store_true")
	parser.add_argument("-ca", "--clean-all", help="Remove .txt and .class files from test folder", action="store_true")
	parser.add_argument("-r", "--run", help="Runs tests even if clean is set", action="store_true")
	
	args = parser.parse_args()

	directory = os.path.join(sys.path[0], args.directory)

	if args.clean_all:
		clean_classes(directory)
		clean_tests(directory)
		if not args.run: return

	if args.clean:
		clean_classes(directory)
	if args.clean_tests:
		clean_tests(directory)
	
	if (args.clean or args.clean_tests) and not args.run:
		return

	config = configparser.RawConfigParser()
	config.read(".env")

	test_files = glob.glob(os.path.join(directory, "*.wtr"))
	total_tests = len(test_files)
	passing_tests = 0
	failing_tests = 0

	for wtr_file in test_files:
		if args.output_record:
			print(f"Generating tests for {wtr_file}...")
		else:
			print(f"Running tests for {wtr_file}...")
		
		compile_process = subprocess.run(["java", 
				"-p",
				f"{config.get('Libraries', 'jcommander')};{config.get('Libraries', 'asm')};{config.get('Libraries', 'runtime')};{config.get('Libraries', 'compiler')}",
				"-m",
				"water.compiler/water.compiler.Main",
				wtr_file
			],
			cwd="D:/Programming/Java/Water/out/production/Compiler",
			capture_output=True,
			text=True
		)
		p_stdout = compile_process.stdout
		p_stderr = compile_process.stderr

		if compile_process.returncode == 0:

			className = os.path.basename(wtr_file).replace(".wtr", "") + "Wtr"

			run_process = subprocess.run([
					"java",
					className
				],
				cwd=directory,
				capture_output=True,
				text=True
			)
			p_stdout += run_process.stdout
			p_stderr += run_process.stderr

		p_stderr = re.sub(
			r"^\[.*\]",
			"[LOC]",
			p_stderr
		)

		if args.output_record:
			with open(wtr_file + ".txt", "w") as f:
				f.write(p_stdout)
				f.write("$stderr:\n")
				f.write(p_stderr)
		else:
			with open(wtr_file + ".txt", "r") as f:
				file = "".join(f.readlines())
				t_stdout, t_stderr = file.split("$stderr:\n")
				
				if t_stdout != p_stdout or t_stderr != p_stderr:
					if args.separate_output:
						if t_stdout != p_stdout:
							print(f"Test in file {wtr_file} failed:\nStdout Expected:\n{t_stdout}\nGot:{p_stdout}", file=sys.stderr)
						if t_stderr != p_stderr:
							print(f"Test in file {wtr_file} failed:\nStderr Expected:\n{t_stderr}\nGot:{p_stderr}", file=sys.stderr)
					else:
						t_output = (t_stdout + '\n' + t_stderr).strip()
						p_output = (p_stdout + '\n' + p_stderr).strip()
						print(f"Test in file {wtr_file} failed:\nExpected Output:\n{t_output}\nGot:\n{p_output}", file=sys.stderr)
						print("(Output is stripped)")
					failing_tests += 1
				else:
					passing_tests += 1

	if not args.output_record:
		print(f"\nRan all tests [{passing_tests} of {total_tests} succeeded] [{failing_tests} of {total_tests} failed]")

if __name__ == "__main__":
	main()