function export_to_idesyde(sourceModel,runPath)
%EXPORT_TO_IDESYDE Summary of this function goes here
%   Detailed explanation goes here
if nargin < 2
    runPath = "run";
end
designPath = fullfile(runPath, "inputs");
mkdir(designPath);
[~, sname, ~] = fileparts(sourceModel);
dest = fullfile(designPath, strcat(sname, ".json"));
s = load_system(sourceModel);
feval(sname, [], [], [], "compile");
m = extract_from_subsystem(s);
jo = jsonencode(m);
fid = fopen(dest, 'w');
fprintf(fid, "%s", jo);
fclose(fid);
feval(sname, [], [], [], "term");
close_system(s);
disp(dest);
% copyfile(sourceModel, fullfile(designPath, strcat(sname, ".slx")));
end

